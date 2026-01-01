import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class changelogGenerate {

    // Configure OneAPI parameters
    private static final String API_BASE_URL = System.getenv("ONEAPI_BASE_URL");
    private static final String API_KEY = System.getenv("ONEAPI_API_KEY");
    private static final String MODEL = "gpt-5-mini";

    public static void main(String[] args) {
        try {
            // Parameters: Git repository path
            String repoPath = "."; // Current directory, or specify path like "/path/to/your/repo"

            System.out.println("Fetching Git commit history...");
            String latestTag = getLatestTag(repoPath);
            System.out.println("Latest tag found: " + latestTag);  // 添加这行调试
            String commits = getGitCommitRange(repoPath, latestTag, "HEAD");

            if (commits.isEmpty()) {
                System.err.println("No commit history found");
                return;
            }

            System.out.println("\nFetched commits:\n" + commits);
            System.out.println("\nCalling OneAPI for summarization...");

            String summary = summarizeChangelog(commits);
            System.out.println("\n=== Changelog Summary ===\n" + summary);
            BufferedWriter out = new BufferedWriter(new FileWriter("CHANGELOG.md"));
            out.write(summary);
            out.close();
            System.out.println("Changelog written to CHANGELOG.md");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get Git commit history
     */
    public static String getGitCommits(String repoPath, int count, String branch)
            throws IOException, InterruptedException {
        // Use git log command with custom format
        // %h: short hash, %an: author name, %ad: author date, %s: subject
        String format = "%h | %an | %ad | %s";

        ProcessBuilder processBuilder = new ProcessBuilder(
                "git",
                "-C", repoPath,  // Specify repository path
                "log",
                "-" + count,     // Get last N commits
                branch,
                "--pretty=format:" + format,
                "--date=short"   // Date format: YYYY-MM-DD
        );

        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Git command failed with exit code: " + exitCode);
        }

        return output.toString().trim();
    }

    /**
     * Get commits between two references (tags, commits, branches)
     */
    public static String getGitCommitRange(String repoPath, String fromCommit, String toCommit)
            throws IOException, InterruptedException {
        String format = "%h | %an | %ad | %s";

        ProcessBuilder processBuilder = new ProcessBuilder(
                "git",
                "-C", repoPath,
                "log",
                fromCommit + ".." + toCommit,
                "--pretty=format:" + format,
                "--date=short",
                "--tags"
        );

        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        process.waitFor();
        return output.toString().trim();
    }

    /**
     * Call OneAPI to summarize commits (optimized based on curl command)
     */
    public static String summarizeChangelog(String commits) throws IOException {
        URL url = new URL(API_BASE_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("POST");

            // Set all request headers (based on curl command)
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);

            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);

            String jsonPayload = buildJsonPayload(commits);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return parseResponse(conn.getInputStream());
            } else {
                String error = readStream(conn.getErrorStream());
                throw new IOException("API request failed, status code: " + responseCode + ", error: " + error);
            }

        } finally {
            conn.disconnect();
        }
    }

    /**
     * Build JSON request payload (matching curl's --data-raw format)
     */
    private static String buildJsonPayload(String commits) {
        String escapedCommits = escapeJson(commits);
        String prompt = escapeJson(
                "Analyze the following Git commit history and generate a clean, structured changelog.\n" +
                        "Format: hash | author | date | message\n\n" +
                        "Output ONLY the categorized changelog in this exact format:\n\n" +
                        "## Features\n" +
                        "- [hash] message\n\n" +
                        "## Bug Fixes\n" +
                        "- [hash] message\n\n" +
                        "## Performance\n" +
                        "- [hash] message\n\n" +
                        "## Documentation\n" +
                        "- [hash] message\n\n" +
                        "## Other\n" +
                        "- [hash] message\n\n" +
                        "Rules:\n" +
                        "1. Do NOT include any introductory text, notes, or explanations\n" +
                        "2. Start directly with '# CHANGELOG'\n" +
                        "3. Only include categories that have commits\n" +
                        "4. Keep commit messages concise\n" +
                        "5. Use markdown format only"
        );

        return String.format("""
            {
              "model": "%s",
              "messages": [
                {
                  "role": "system",
                  "content": "You are a changelog generator. Output only the formatted changelog without any additional commentary."
                },
                {
                  "role": "user",
                  "content": "%s\\n\\nCommit History:\\n%s"
                }
              ],
              "max_completion_tokens": 100000,
              "top_p": 1,
              "presence_penalty": 0,
              "frequency_penalty": 0,
              "stream": false
            }
            """, MODEL, prompt, escapedCommits);
    }

    /**
     * Parse API response and extract content field
     */
    private static String parseResponse(InputStream is) throws IOException {
        String response = readStream(is);
        int contentIndex = response.indexOf("\"content\"");
        if (contentIndex == -1) {
            return response;
        }

        int startQuote = response.indexOf("\"", contentIndex + 10);
        int endQuote = findClosingQuote(response, startQuote + 1);

        if (startQuote != -1 && endQuote != -1) {
            String content = response.substring(startQuote + 1, endQuote);
            return unescapeJson(content);
        }

        return response;
    }

    /**
     * Read input stream content
     */
    private static String readStream(InputStream is) throws IOException {
        if (is == null) return "";

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

    /**
     * Escape JSON string
     */
    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Unescape JSON string
     */
    private static String unescapeJson(String text) {
        return text.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    /**
     * Find the position of unescaped closing quote
     */
    private static int findClosingQuote(String str, int start) {
        for (int i = start; i < str.length(); i++) {
            if (str.charAt(i) == '"' && str.charAt(i - 1) != '\\') {
                return i;
            }
        }
        return -1;
    }

    /**
     * Get the latest tag in the repository
     */
    public static String getLatestTag(String repoPath) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "git",
                "-C", repoPath,
                "describe",
                "--tags",
                "--abbrev=0"
        );

        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Git command failed with exit code: " + exitCode + ". No tags found?");
        }

        return output.toString().trim();
    }
}
