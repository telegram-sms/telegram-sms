#!/usr/bin/env bash

set -euo pipefail

if [ "$#" -ne 3 ]; then
    echo "Usage: $0 <previous-tag> <revision> <output-file>" >&2
    exit 2
fi

previous_tag="$1"
revision="$2"
output_file="$3"

if ! git rev-parse --verify "${previous_tag}^{commit}" > /dev/null 2>&1; then
    echo "ERROR: Changelog base tag does not exist: ${previous_tag}" >&2
    exit 1
fi
if ! git rev-parse --verify "${revision}^{commit}" > /dev/null 2>&1; then
    echo "ERROR: Changelog revision does not exist: ${revision}" >&2
    exit 1
fi
if ! git merge-base --is-ancestor "${previous_tag}" "${revision}"; then
    echo "ERROR: ${previous_tag} is not an ancestor of ${revision}" >&2
    exit 1
fi

temp_dir="$(mktemp -d)"
trap 'rm -rf -- "${temp_dir}"' EXIT

sections=(breaking security features fixes performance documentation maintenance)
for section in "${sections[@]}"; do
    : > "${temp_dir}/${section}"
done

commit_count=0
breaking_subject_re='^[[:alpha:]]+(\([^)]*\))?!:'
declare -A seen_patch_ids=()
while IFS=$'\t' read -r full_hash short_hash subject; do
    [ -n "${full_hash}" ] || continue

    # A nightly change may later be rebased or cherry-picked onto master. Keep
    # only the newest occurrence of an identical patch in the release notes.
    patch_line="$(git show --no-ext-diff --pretty=format: "${full_hash}" | git patch-id --stable)"
    patch_id="${patch_line%% *}"
    if [ -n "${patch_id}" ] && [ -n "${seen_patch_ids[${patch_id}]:-}" ]; then
        continue
    fi
    if [ -n "${patch_id}" ]; then
        seen_patch_ids["${patch_id}"]=1
    fi

    commit_count=$((commit_count + 1))

    message="$(git show -s --format=%B "${full_hash}")"
    normalized_subject="$(printf '%s' "${subject}" | tr '[:upper:]' '[:lower:]')"

    if [[ "${subject}" =~ ${breaking_subject_re} ]] || \
       printf '%s\n' "${message}" | grep -qiE '^BREAKING[ -]CHANGE:'; then
        section="breaking"
    else
        conventional_type="${normalized_subject%%:*}"
        conventional_scope=""
        if [[ "${conventional_type}" == *"("* && "${conventional_type}" == *")" ]]; then
            conventional_scope="${conventional_type#*(}"
            conventional_scope="${conventional_scope%)}"
        fi
        conventional_type="${conventional_type%%(*}"
        conventional_type="${conventional_type%!}"
        if [[ "${conventional_scope}" =~ ^(ci|build|test|tests)$ ]]; then
            section="maintenance"
        else
            case "${conventional_type}" in
                feat) section="features" ;;
                fix) section="fixes" ;;
                perf) section="performance" ;;
                docs) section="documentation" ;;
                security) section="security" ;;
                *) section="maintenance" ;;
            esac
        fi
    fi

    printf -- '- [`%s`](https://github.com/%s/%s/commit/%s) %s\n' \
        "${short_hash}" "${OWNER:-telegram-sms}" "${REPO:-telegram-sms}" \
        "${full_hash}" "${subject}" >> "${temp_dir}/${section}"
done < <(git log --no-merges --format='%H%x09%h%x09%s' "${previous_tag}..${revision}")

if [ "${commit_count}" -eq 0 ]; then
    echo "ERROR: No non-merge commits found in ${previous_tag}..${revision}" >&2
    exit 1
fi

: > "${output_file}"
write_section() {
    local title="$1"
    local file="$2"
    if [ -s "${file}" ]; then
        printf '## %s\n\n' "${title}" >> "${output_file}"
        cat "${file}" >> "${output_file}"
        printf '\n' >> "${output_file}"
    fi
}

write_section "⚠️ Breaking Changes" "${temp_dir}/breaking"
write_section "Security" "${temp_dir}/security"
write_section "Features" "${temp_dir}/features"
write_section "Bug Fixes" "${temp_dir}/fixes"
write_section "Performance" "${temp_dir}/performance"
write_section "Documentation" "${temp_dir}/documentation"
write_section "Maintenance" "${temp_dir}/maintenance"
