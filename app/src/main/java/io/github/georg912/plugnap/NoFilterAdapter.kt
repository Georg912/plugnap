package io.github.georg912.plugnap

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter

/**
 * ArrayAdapter whose filter always returns the complete list.
 *
 * REGRESSION NOTE: after an activity re-creation (e.g. theme switch) the
 * default adapter filters the dropdown list down to the restored field
 * text — only one entry remains selectable and position indices no longer
 * line up. Therefore: never filter, and always resolve the selection via
 * [labelToValue] instead of the popup position.
 */
internal class NoFilterAdapter(context: Context, items: Array<String>) :
    ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, items) {

    private val all = items

    override fun getFilter(): Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?) =
            FilterResults().apply { values = all; count = all.size }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) =
            notifyDataSetChanged()
    }
}

/**
 * Maps a tapped dropdown label to its value — independent of the
 * (potentially filtered) popup position.
 */
internal fun labelToValue(labels: Array<String>, values: IntArray, label: String): Int =
    values[labels.indexOf(label).coerceAtLeast(0)]
