package com.lvvi.vividtv.model

/**
 * 用户自定义输入的播放源
 */
data class CustomSourceHistory(
    var link: String = "",
    var time: Long = 0
) {
    override fun equals(other: Any?): Boolean {
        if (other is CustomSourceHistory) {
            if (other.link == this.link) {
                return true
            }
        }
        return super.equals(other)
    }
}