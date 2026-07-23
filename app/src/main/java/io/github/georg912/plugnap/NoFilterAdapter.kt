package io.github.georg912.plugnap

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter

/**
 * ArrayAdapter, dessen Filter immer die komplette Liste liefert.
 *
 * REGRESSION-HINWEIS: Der Standard-Adapter filtert nach einer Activity-
 * Neuerstellung (z. B. Theme-Wechsel) die Dropdown-Liste auf den
 * wiederhergestellten Feldtext zusammen — dann ist nur noch ein Eintrag
 * wählbar und Positions-Indizes stimmen nicht mehr. Deshalb: niemals
 * filtern, und die Auswahl immer über [labelToValue] statt über die
 * Popup-Position auflösen.
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
 * Ordnet ein angeklicktes Dropdown-Label dem zugehörigen Wert zu —
 * unabhängig von der (potenziell gefilterten) Popup-Position.
 */
internal fun labelToValue(labels: Array<String>, values: IntArray, label: String): Int =
    values[labels.indexOf(label).coerceAtLeast(0)]
