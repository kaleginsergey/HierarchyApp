package com.example.hierarchyapp

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class TreeAdapter(
    private val visibleNodes: MutableList<TreeNode>
) : RecyclerView.Adapter<TreeAdapter.NodeViewHolder>() {

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
        (holder.indentGuide.layoutParams as ViewGroup.MarginLayoutParams).width = indentPx
        holder.indentGuide.requestLayout()

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

        // Arrow for expandable nodes
        if (node.hasChildren) {
            holder.arrowIcon.visibility = View.VISIBLE
            val rotation = if (node.isExpanded) 90f else 0f
            holder.arrowIcon.rotation = rotation
        } else {
            holder.arrowIcon.visibility = View.INVISIBLE
        }

        // Click to expand/collapse
        holder.itemView.setOnClickListener {
            if (node.hasChildren) {
                toggleNode(node, holder.bindingAdapterPosition)
                // Animate arrow
                val targetRotation = if (node.isExpanded) 90f else 0f
                ObjectAnimator.ofFloat(holder.arrowIcon, "rotation", targetRotation).apply {
                    duration = 200
                    start()
                }
            }
        }
    }

    override fun getItemCount() = visibleNodes.size

    private fun toggleNode(node: TreeNode, position: Int) {
        if (node.isExpanded) {
            collapseNode(node, position)
        } else {
            expandNode(node, position)
        }
    }

    private fun expandNode(node: TreeNode, position: Int) {
        node.isExpanded = true
        val insertList = getVisibleDescendants(node)
        visibleNodes.addAll(position + 1, insertList)
        notifyItemChanged(position)
        notifyItemRangeInserted(position + 1, insertList.size)
    }

    private fun collapseNode(node: TreeNode, position: Int) {
        node.isExpanded = false
        val removeCount = countVisibleDescendants(node, position)
        repeat(removeCount) { visibleNodes.removeAt(position + 1) }
        notifyItemChanged(position)
        notifyItemRangeRemoved(position + 1, removeCount)
        // Recursively mark all children as collapsed
        collapseChildrenState(node)
    }

    private fun collapseChildrenState(node: TreeNode) {
        node.children.forEach { child ->
            child.isExpanded = false
            collapseChildrenState(child)
        }
    }

    private fun getVisibleDescendants(node: TreeNode): List<TreeNode> {
        val list = mutableListOf<TreeNode>()
        node.children.forEach { child ->
            list.add(child)
            if (child.isExpanded) {
                list.addAll(getVisibleDescendants(child))
            }
        }
        return list
    }

    private fun countVisibleDescendants(node: TreeNode, position: Int): Int {
        var count = 0
        node.children.forEach { child ->
            count++
            if (child.isExpanded) {
                count += countVisibleDescendants(child, -1)
            }
        }
        return count
    }

    fun filter(query: String, allNodes: List<TreeNode>) {
        visibleNodes.clear()
        if (query.isEmpty()) {
            // Restore default view: top-level expanded
            allNodes.forEach { node ->
                visibleNodes.add(node)
                if (node.isExpanded) {
                    addExpandedChildren(node)
                }
            }
        } else {
            // Show all matching nodes with parents expanded
            val q = query.lowercase()
            allNodes.forEach { client ->
                addMatchingNodes(client, q)
            }
        }
        notifyDataSetChanged()
    }

    private fun addExpandedChildren(node: TreeNode) {
        node.children.forEach { child ->
            visibleNodes.add(child)
            if (child.isExpanded) addExpandedChildren(child)
        }
    }

    private fun addMatchingNodes(node: TreeNode, query: String): Boolean {
        val selfMatches = node.label.lowercase().contains(query)
        var anyChildMatches = false

        node.children.forEach { child ->
            if (addMatchingNodes(child, query)) anyChildMatches = true
        }

        if (selfMatches || anyChildMatches) {
            if (!visibleNodes.contains(node)) {
                visibleNodes.add(node)
                if (anyChildMatches) {
                    node.children.forEach { child ->
                        addMatchingNodes(child, query)
                    }
                }
            }
            return true
        }
        return false
    }
}
