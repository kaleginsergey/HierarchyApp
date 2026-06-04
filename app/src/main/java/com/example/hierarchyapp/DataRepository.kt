package com.example.hierarchyapp

object DataRepository {

    // Data from Список_объектов_ПР.xlsx
    // Columns: Клиент | Адрес | Объект | Узел
    private val rawData = listOf(
        listOf("МУП Водоканал", "Центральный офис (ул. Царская, д4)", "ФС \"Светлый родник\" (4 этаж)", "Пурифаер (1 этаж, оф.4)"),
        listOf("МУП Водоканал", "Центральный офис (ул. Царская, д4)", "ФС \"Светлый родник\" (4 этаж)", "Пурифаер (2 этаж, оф.101)"),
        listOf("МУП Водоканал", "Центральный офис (ул. Царская, д4)", "Water Tower (подвал)", "Кран питьевой (1 этаж, блок А, оф.3)"),
        listOf("МУП Водоканал", "Центральный офис (ул. Царская, д4)", "Water Tower (подвал)", "Пурифаер (1 этаж, холл)"),
        listOf("МУП Водоканал", "АТП №5 (Декабристов, д.103)", "ФС Atoll 3800", "Пурифаер (столовая)"),
        listOf("Банк Точка", "БЦ Саммит (Белинского, 4)", "Water Box (14 этаж, оф 100)", "Пурифаер (14 этаж, коридор)")
    )

    fun buildTree(): List<TreeNode> {
        val clientMap = linkedMapOf<String, TreeNode>()

        rawData.forEach { row ->
            val clientName = row[0]
            val addressName = row[1]
            val objectName = row[2]
            val nodeName = row[3]

            // Level 0: Client
            val clientNode = clientMap.getOrPut(clientName) {
                TreeNode(id = "c_$clientName", label = clientName, level = 0, isExpanded = true)
            }

            // Level 1: Address
            val addressKey = "$clientName|$addressName"
            val addressNode = clientNode.children.find { it.id == "a_$addressKey" }
                ?: TreeNode(id = "a_$addressKey", label = addressName, level = 1, isExpanded = true)
                    .also { clientNode.children.add(it) }

            // Level 2: Object
            val objectKey = "$addressKey|$objectName"
            val objectNode = addressNode.children.find { it.id == "o_$objectKey" }
                ?: TreeNode(id = "o_$objectKey", label = objectName, level = 2, isExpanded = false)
                    .also { addressNode.children.add(it) }

            // Level 3: Node (leaf)
            val nodeKey = "$objectKey|$nodeName"
            if (objectNode.children.none { it.id == "n_$nodeKey" }) {
                objectNode.children.add(
                    TreeNode(id = "n_$nodeKey", label = nodeName, level = 3)
                )
            }
        }

        return clientMap.values.toList()
    }
}
