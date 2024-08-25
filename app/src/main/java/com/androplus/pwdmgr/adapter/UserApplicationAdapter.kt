package com.androplus.pwdmgr.adapter
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import com.androplus.pwdmgr.R
import com.androplus.pwdmgr.model.UserApplication

class UserApplicationAdapter(context: Context, private val resourceId: Int, userApplicationList: List<UserApplication>): ArrayAdapter<UserApplication>(context, resourceId, userApplicationList), Filterable {
    private val results : List<UserApplication> = userApplicationList

    override fun getCount() = results.size

    override fun getItem(position: Int) = results[position]

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(resourceId, parent, false)
        val textView = view.findViewById<TextView>(R.id.app_text)
        textView.text = getItem(position).app_name
        return view
    }

    override fun getFilter() = object : Filter(){
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val filterResults = FilterResults()
            //val dbResults = RealmService.getInstance().getAllUserApplications(constraint.toString())
           // filterResults.values = dbResults
            //filterResults.count = dbResults.size
            //results.clear()
            //results.addAll(dbResults)
            return filterResults
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            if((results != null) && (results.count > 0)){
                notifyDataSetChanged()
            }
            else{
                notifyDataSetInvalidated()
            }
        }

        override fun convertResultToString(resultValue: Any?): String? {
            val searchResult = resultValue as UserApplication
            return searchResult.app_name
        }
    }
}