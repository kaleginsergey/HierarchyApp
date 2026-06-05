package com.example.hierarchyapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class TreeAdapter : RecyclerView.Adapter<TreeAdapter.NodeViewHolder>() {

    private val visibleNodes: MutableList<TreeNode> = mutableListOf()

    inner class NodeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val labelText: TextView = view.findViewById(R.id.tvLabel)
        val levelBadge: TextView = view.findViewById(R.id.tvLevelBadge)
        val arrowIcon: ImageView = view.findViewById(R.id.ivArrow)
        val indentGuide: View = view.findViewById(R.id.indentGuide)
        val container: View = view.findViewById(R.id.nodeContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tree_node, parent, false)
        return NodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
        val node = visibleNodes[position]
        val ctx = holder.itemView.context

        holder.labelText.text = node.label
        holder.levelBadge.text = node.levelName

        // Indent based on level
        val dp = ctx.resources.displayMetrics.density
        val indentPx = (node.level * 20 * dp).toInt()
        val lp = holder.indentGuide.layoutParams
        lp.width = indentPx
        holder.indentGuide.layoutParams = lp

        // Color per level
        val (bgColor, badgeColor) = when (node.level) {
            0 -> Pair(R.color.level0_bg, R.color.level0_accent)
            1 -> Pair(R.color.level1_bg, R.color.level1_accent)
            2 -> Pair(R.color.level2_bg, R.color.level2_accent)
            else -> Pair(R.color.level3_bg, R.color.level3_accent)
        }
        holder.container.setBackgroundColor(ContextCompat.getColor(ctx, bgColor))
        holder.levelBadge.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(ctx, badgeColor)
        )

        // Arrow
        if (node.hasChildren) {
            holder.arrowIcon.visibility = View.VISIBLE
            holder.arrowIcon.rotation = if (node.isExpanded) 90f else 0f
        } else {
            holder.arrowIcon.visibility = View.INVISIBLE
        }

        // Click — use adapterPosition captured at click time, not at bind time
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_ID.toInt()) return@setOnClickListener
            if (pos < 0 || pos >= visibleNodes.size) return@setOnClickListener
            val clickedNode = visibleNodes[pos]
            if (!clickedNode.hasChildren) return@setOnClickListener
            if (clickedNode.isExpanded) {
                collapseNode(pos)
            } else {
                expandNode(pos)
            }
        }
    }

    override fun getItemCount() = visibleNodes.size

    private fun expandNode(position: Int) {
        val node = visibleNodes[position]
        node.isExpanded = true
        val toInsert = flatChildren(node)
        if (toInsert.isEmpty()) return
        visibleNodes.addAll(position + 1, toInsert)
        notifyItemChanged(position)
        notifyItemRangeInserted(position + 1, toInsert.size)
    }

    private fun collapseNode(position: Int) {
        val node = visibleNodes[position]
        node.isExpanded = false
        // Count how many visible items belong to this node's subtree
        var count = 0
        var i = position + 1
        while (i < visibleNodes.size && visibleNodes[i].level > node.level) {
            count++
            i++
        }
        if (count == 0) return
        repeat(count) { visibleNodes.removeAt(position + 1) }
        // Mark all descendants as collapsed so re-expanding starts fresh
        markCollapsed(node)
        notifyItemChanged(position)
        notifyItemRangeRemoved(position + 1, count)
    }

    private fun markCollapsed(node: TreeNode) {
        node.isExpanded = false
        node.children.forEach { markCollapsed(it) }
    }

    // Returns direct children only (not grandchildren — they get added on demand)
    private fun flatChildren(node: TreeNode): List<TreeNode> {
        val list = mutableListOf<TreeNode>()
        node.children.forEach { child ->
            list.add(child)
            if (child.isExpanded) {
                list.addAll(flatChildren(child))
            }
        }
        return list
    }

    // ── Public API ──────────────────────────────────────────────────────────

    fun setRoots(roots: List<TreeNode>) {
        visibleNodes.clear()
        roots.forEach { root ->
            visibleNodes.add(root)
            if (root.isExpanded) visibleNodes.addAll(flatChildren(root))
        }
        notifyDataSetChanged()
    }

    fun filter(query: String, allNodes: List<TreeNode>) {
        visibleNodes.clear()
        if (query.isBlank()) {
            allNodes.forEach { root ->
                visibleNodes.add(root)
                if (root.isExpanded) visibleNodes.addAll(flatChildren(root))
            }
        } else {
            val q = query.trim().lowercase()
            allNodes.forEach { root -> collectMatches(root, q) }
        }
        notifyDataSetChanged()
    }

    private fun collectMatches(node: TreeNode, query: String): Boolean {
        val selfMatch = node.label.lowercase().contains(query)
        val childMatches = node.children.map { collectMatches(it, query) }
        val anyChild = childMatches.any { it }
        if (selfMatch || anyChild) {
            visibleNodes.add(node)
            node.children.forEachIndexed { i, child ->
                if (childMatches[i]) collectMatches_addVisible(child, query)
            }
            return true
        }
        return false
    }

    private fun collectMatches_addVisible(node: TreeNode, query: String) {
        val selfMatch = node.label.lowercase().contains(query)
        val childMatches = node.children.map { child ->
            child.label.lowercase().contains(query) ||
                    child.children.any { it.label.lowercase().contains(query) }
        }
        val anyChild = childMatches.any { it }
        if (selfMatch || anyChild) {
            visibleNodes.add(node)
            node.children.forEachIndexed { i, child ->
                if (childMatches[i]) collectMatches_addVisible(child, query)
            }
        }
    }
}
