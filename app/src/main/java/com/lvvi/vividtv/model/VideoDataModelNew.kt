package com.lvvi.vividtv.model

/**
 * 电视直播源对象
 */
class VideoDataModelNew {
    var id: String? = null
    var name: String? = null
    var icon: String? = null
    var url1: String? = null
    var url2: String? = null
    var title: String? = null
    var startTime: String? = null
    var endTime: String? = null

    override fun toString(): String {
        return "id: $id, name: $name, icon: $icon, url1: $url1, url2: $url2, title: $title, startTime: $startTime, endTime: $endTime"
    }
}
