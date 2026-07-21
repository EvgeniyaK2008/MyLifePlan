package com.example.mylifeplan

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


class PlansAdapter(
    private val plansList: MutableList<PlanItem>,
    private val onDeleteClick: (Int) -> Unit // Лямбда-функция для обработки удаления
) : RecyclerView.Adapter<PlansAdapter.PlanViewHolder>() {

    class PlanViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTime: TextView = itemView.findViewById(R.id.tv_time)
        val tvTask: TextView = itemView.findViewById(R.id.tv_task)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete_plan)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlanViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_plan, parent, false)
        return PlanViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlanViewHolder, position: Int) {
        val item = plansList[position]
        holder.tvTime.text = item.time
        holder.tvTask.text = item.task

        // Обработка нажатия на крестик удаления
        holder.btnDelete.setOnClickListener {
            onDeleteClick(holder.adapterPosition)
        }
    }

    override fun getItemCount(): Int = plansList.size
}