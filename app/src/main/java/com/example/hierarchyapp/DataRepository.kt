package com.example.hierarchyapp

object DataRepository {

    // Лист1: №п/п | Клиент | Адрес | Объект | Узел
    private val rawData = listOf(
        listOf("1", "МУП Водоканал", "Центральный офис (ул. Царская, д4)", "ФС \"Светлый родник\" (4 этаж)", "Пурифаер (1 этаж, оф.4)"),
        listOf("2", "МУП Водоканал", "Центральный офис (ул. Царская, д4)", "ФС \"Светлый родник\" (4 этаж)", "Пурифаер (2 этаж, оф.101)"),
        listOf("3", "МУП Водоканал", "Центральный офис (ул. Царская, д4)", "Water Tower (подвал)", "Кран питьевой (1 этаж, блок А, оф.3)"),
        listOf("4", "МУП Водоканал", "Центральный офис (ул. Царская, д4)", "Water Tower (подвал)", "Пурифаер (1 этаж, холл)"),
        listOf("5", "МУП Водоканал", "АТП №5 (Декабристов, д.103)", "ФС Atoll 3800", "Пурифаер (столовая)"),
        listOf("6", "Банк Точка", "БЦ Саммит (Белинского, 4)", "Water Box (14 этаж, оф 100)", "Пурифаер (14 этаж, коридор)")
    )

    // Лист2: №узла | Класс | Серийный номер | Расположение | Лимит потребления | Геоданные
    val nodeDetails: Map<Int, NodeDetail> = mapOf(
        1 to NodeDetail(1, "Пурифаер",       "1111",  "подвал",   "200",  "56.835515, 60.589228"),
        2 to NodeDetail(2, "Пурифаер",       "2222",  "3 этаж",   "100",  "56.837504, 60.586874"),
        3 to NodeDetail(3, "Кран питьевой",  "44555", "холл",     "300",  "56.835855, 60.595570"),
        4 to NodeDetail(4, "Пурифаер",       "5555",  "офис",     "4000", "56.832979, 60.587925"),
        5 to NodeDetail(5, "Пурифаер",       "66666", "кабинет",  "500",  "56.834117, 60.584709"),
        6 to NodeDetail(6, "Пурифаер",       "7777",  "директор", "300",  "56.834003, 60.585760")
    )

    fun buildTree(): List<TreeNode> {
        val clientMap = linkedMapOf<String, TreeNode>()

        rawData.forEach { row ->
            val nodeId   = row[0].toInt()
            val clientName  = row[1]
            val addressName = row[2]
            val objectName  = row[3]
            val nodeName    = row[4]

            val clientNode = clientMap.getOrPut(clientName) {
                TreeNode(id = "c_$clientName", label = clientName, level = 0, isExpanded = true)
            }

            val addressKey = "$clientName|$addressName"
            val addressNode = clientNode.children.find { it.id == "a_$addressKey" }
                ?: TreeNode(id = "a_$addressKey", label = addressName, level = 1, isExpanded = true)
                    .also { clientNode.children.add(it) }

            val objectKey = "$addressKey|$objectName"
            val objectNode = addressNode.children.find { it.id == "o_$objectKey" }
                ?: TreeNode(id = "o_$objectKey", label = objectName, level = 2, isExpanded = false)
                    .also { addressNode.children.add(it) }

            val nodeKey = "$objectKey|$nodeName"
            if (objectNode.children.none { it.id == "n_$nodeKey" }) {
                objectNode.children.add(
                    TreeNode(id = "n_$nodeKey", label = nodeName, level = 3, nodeId = nodeId)
                )
            }
        }

        return clientMap.values.toList()
    }
}
