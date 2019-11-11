package com.github.sumimakito.codeauxlib

@Suppress("SpellCheckingInspection")
class CodeauxLibStatic {
    companion object {
        private val parser = CodeauxLibPortable()
        @JvmStatic
        fun parsecode(body: String?): String? {
            return parser.find(body!!)
        }
    }
}