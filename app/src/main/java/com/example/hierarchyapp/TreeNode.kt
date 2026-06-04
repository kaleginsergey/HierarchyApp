package com.example.hierarchyapp

data class TreeNode(
    val id: String,
    val label: String,
    val level: Int,           // 0=Клиент, 1=Адрес, 2=Объект, 3=Узел
    val children: MutableList<TreeNode> = mutableListOf(),
    var isExpanded: Boolean = false
) {
    val levelName: String get() = when (level) {
        0 -> "Клиент"
        1 -> "Адрес"
        2 -> "Объект"
        3 -> "Узел"
        else -> ""
    }

    val hasChildren: Boolean get() = children.isNotEmpty()
}
