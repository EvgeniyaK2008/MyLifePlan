package com.example.mylifeplan

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


class MealsAdapter(
    private val mealsList: MutableList<MealItem>,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<MealsAdapter.MealViewHolder>() {

    class MealViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMealName: TextView = itemView.findViewById(R.id.tv_meal_name)
        val tvMealCalories: TextView = itemView.findViewById(R.id.tv_meal_calories)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete_meal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MealViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_meal, parent, false)
        return MealViewHolder(view)
    }

    override fun onBindViewHolder(holder: MealViewHolder, position: Int) {
        val item = mealsList[position]
        holder.tvMealName.text = item.name
        holder.tvMealCalories.text = "${item.calories} kcal"

        holder.btnDelete.setOnClickListener {
            onDeleteClick(holder.adapterPosition)
        }
    }

    override fun getItemCount(): Int = mealsList.size
}