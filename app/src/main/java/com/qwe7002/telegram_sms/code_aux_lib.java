// Copyright (c) 2019 Makito
// All rights reserved.
//
// REGULAR EXPRESSIONS FOR VERIFICATION CODE EXTRACTION ARE CONSTRUCTED BY MAKITO
//
// Licensed Under The 3-Clause BSD License
//
// Redistribution and use in source and binary forms, with or without modification,
// are permitted provided that the following conditions are met:
//
//  - Redistributions of source code must retain the above copyright notice,
//    this list of conditions and the following disclaimer.
//
//  - Redistributions in binary form must reproduce the above copyright notice,
//    this list of conditions and the following disclaimer in the documentation
//    and/or other materials provided with the distribution.
//
//  - Neither the name of the copyright holder nor the names its contributors may
//    be used to endorse or promote products derived from this software without
//    specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
// ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
// ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.qwe7002.telegram_sms;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class code_aux_lib {
    private static final String[] REGEXP_OMIT = new String[]{
            "(https?|ftp|file):\\/\\/[-a-zA-Z0-9+&@#\\/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#\\/%=~_|]",
    };

    private static final String[] REGEXP_REPLACE = new String[]{
            "\\n",
            "[\\[\\]【】『』「」“”‘’\"'\\{\\}\\<\\>《》]",
            "([^\\d]|^)\\d{2}[\\/\\-.年月日]\\d{2}[\\/\\-.年月日]\\d{4}[年月日]?([^\\d]|$)",
    };

    private static final String[] REGEXP_EXTRACT = new String[]{
            ".*?(\\d{6,10}|\\d+[-\\s]\\d+?[-\\s]\\d+|\\d+[-\\s]\\d+|\\d{4,10})\\s*(?:[为為是：:]).*?(?:(?:[認驗]證|[认验]证|校[檢检验驗]|安全|登[錄录入]|密|身份|[確确][認认]|pin\\s*)\\s*[编号編號]?\\s*[码碼]).*",
            ".*?(?:(?:[認驗]證|[认验]证|校[檢检验驗]|安全|登[錄录入]|密|身份|[確确][認认]|pin\\s*)\\s*[编号編號]?\\s*[码碼]).*?(?:[为為是：:])\\s*(\\d{6,10}|\\d+[-\\s]\\d+?[-\\s]\\d+|\\d+[-\\s]\\d+|\\d{4,10}).*",
            ".*?(\\d{6,10}|\\d+[-\\s]\\d+?[-\\s]\\d+|\\d+[-\\s]\\d+|\\d{4,10}).*?(?:(?:[認驗]證|[认验]证|校[檢检验驗]|安全|登[錄录入]|密|身份|[確确][認认]|pin\\s*)\\s*[编号編號]?\\s*[码碼]).*",
            ".*?(?:(?:[認驗]證|[认验]证|校[檢检验驗]|安全|登[錄录入]|密|身份|[確确][認认]|pin\\s*)\\s*[编号編號]?\\s*[码碼]).*?(\\d{6,10}|\\d+[-\\s]\\d+?[-\\s]\\d+|\\d+[-\\s]\\d+|\\d{4,10}).*",
            ".*?(?:使用|[輸输]入|粘[贴貼]|[複复][製制]).*?[:：]?\\s*(\\d{6,10}|\\d+[-\\s]\\d+?[-\\s]\\d+|\\d+[-\\s]\\d+|\\d{4,10}).*",
            ".*?(\\d{6,10}|\\d+[-\\s]\\d+?[-\\s]\\d+|\\d+[-\\s]\\d+|\\d{4,10})\\s*(?:[はが：:]).*?(?:認証|セキュリティー?|pin\\s*)\\s*(?:コード|番号).*",
            ".*?(?:認証|セキュリティー?|pin\\s*)\\s*(?:コード|番号).*?(?:[はが：:])\\s*(\\d{6,10}|\\d+[-\\s]\\d+?[-\\s]\\d+|\\d+[-\\s]\\d+|\\d{4,10}).*",
            ".*?(\\d{6,10}|\\d+[-\\s]\\d+?[-\\s]\\d+|\\d+[-\\s]\\d+|\\d{4,10})\\s*(?:[はが：:]).*?(?:コード|番号).*",
            ".*?(?:コード|番号).*?(?:[はが：:])\\s*(\\d{6,10}|\\d+[-\\s]\\d+?[-\\s]\\d+|\\d+[-\\s]\\d+|\\d{4,10}).*",
            ".*?(\\d{6,10}|\\d+[-\\s]\\d+?[-\\s]\\d+|\\d+[-\\s]\\d+|\\d{4,10}).*?(?:認証|セキュリティー?|pin\\s*)\\s*(?:コード|番号).*",
            ".*?(?:認証|セキュリティー?|pin\\s*)\\s*(?:コード|番号).*?(\\d{6,10}|\\d+[-\\s]\\d+?[-\\s]\\d+|\\d+[-\\s]\\d+|\\d{4,10}).*",
            ".*?(?:入力|貼り付け|コーピ|切り取り|ペースト).*?[:：]?\\s*(\\d{6,10}|\\d+[-\\s]\\d+?[-\\s]\\d+|\\d+[-\\s]\\d+|\\d{4,10}).*",
            ".*?(?:verification|security|auth(?:entication)?|login|identification|sign\\-?in).*?(?:is|:|：|\\s*)\\s*(?!\\d+ (?:sec(?:ond)?s?|min(?:ute)?s?|hours?|hrs))(\\d{6,10}|\\d+[-\\s]\\d+?[-\\s]\\d+|\\d+[-\\s]\\d+|\\d{4,10}).*",
            ".*?(?!\\d+ (?:sec(?:ond)?s?|min(?:ute)?s?|hours?|hrs))(\\d{6,10}|\\d+[-\\s]\\d+?[-\\s]\\d+|\\d+[-\\s]\\d+|\\d{4,10})(?:\\s*is).*?(?:verification|security|auth(?:entication)?|login|identification|sign\\-?in).*",
            ".*?(?:code|password|pin).*?(?:is|:|：|\\s*)\\s*(?!\\d+ (?:sec(?:ond)?s?|min(?:ute)?s?|hours?|hrs))(\\d{6,10}|\\d+[-\\s]\\d+?[-\\s]\\d+|\\d+[-\\s]\\d+|\\d{4,10}).*",
            ".*?(?:use|enter|paste).*?(?:is|:|：|\\s*)\\s*(?!\\d+ (?:sec(?:ond)?s?|min(?:ute)?s?|hours?|hrs))(\\d{6,10}|\\d+[-\\s]\\d+?[-\\s]\\d+|\\d+[-\\s]\\d+|\\d{4,10}).*",
            ".*?(?!\\d+ (?:sec(?:ond)?s?|min(?:ute)?s?|hours?|hrs))(\\d{6,10}|\\d+[-\\s]\\d+?[-\\s]\\d+|\\d+[-\\s]\\d+|\\d{4,10}).*?(?:verification|security|auth(?:entication)?|login|identification|sign\\-?in).*",
    };


    private static final ArrayList<Pattern> patterns = new ArrayList<>();

    code_aux_lib() {
        for (String rs : REGEXP_EXTRACT) {
            patterns.add(Pattern.compile(rs, Pattern.CASE_INSENSITIVE));
        }
    }

    String find(String input) {
        for (String rs : REGEXP_OMIT) {
            input = input.replaceAll(rs, "");
        }
        for (String rs : REGEXP_REPLACE) {
            input = input.replaceAll(rs, " ");
        }
        for (Pattern p : patterns) {
            Matcher matcher = p.matcher(input);
            if (matcher.find()) {
                return matcher.group(1).replaceAll("[\\s-]", "");
            }
        }
        return null;
    }
}
