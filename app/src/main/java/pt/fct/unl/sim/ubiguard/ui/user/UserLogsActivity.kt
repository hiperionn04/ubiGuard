package pt.fct.unl.sim.ubiguard.ui.user

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import pt.fct.unl.sim.ubiguard.R
import pt.fct.unl.sim.ubiguard.adapters.LogAdapter
import pt.fct.unl.sim.ubiguard.ui.base.BaseActivity

class UserLogsActivity : BaseActivity() {

    private val logList = mutableListOf<String>()
    private lateinit var logAdapter: LogAdapter
    private lateinit var database: DatabaseReference
    private var alarmId: String? = null
    private var isLoading = false
    private var isLastPage = false
    private var oldestKey: String? = null
    private val PAGE_SIZE = 50
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var rvLogs: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_logs)

        alarmId = intent.getStringExtra("ALARM_ID")
        if (alarmId == null) {
            Toast.makeText(this, getString(R.string.error_alarm_notfound), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val ivMenuIcon = findViewById<ImageView>(R.id.ivMenuIcon)
        ativarSliderComponent(drawerLayout, ivMenuIcon)

        rvLogs = findViewById(R.id.rvLogs)
        progressBar = findViewById(R.id.progressBarLogs)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        val layoutManager = LinearLayoutManager(this)
        rvLogs.layoutManager = layoutManager
        logAdapter = LogAdapter(logList)
        rvLogs.adapter = logAdapter

        database = FirebaseDatabase.getInstance().reference

        carregarLogs(isInitialLoad = true)

        rvLogs.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                if (dy > 0) {
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if (!isLoading && !isLastPage) {
                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount && firstVisibleItemPosition >= 0) {
                            carregarLogs(isInitialLoad = false)
                        }
                    }
                }
            }
        })
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun carregarLogs(isInitialLoad: Boolean) {
        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE

        if (isInitialLoad) {
            logList.clear()
            logAdapter.notifyDataSetChanged()
        }

        var query = database.child("alarms").child(alarmId!!).child("logs").orderByKey()

        if (isInitialLoad) {
            query = query.limitToLast(PAGE_SIZE)
        } else {
            query = query.endAt(oldestKey).limitToLast(PAGE_SIZE)
        }

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                progressBar.visibility = View.GONE
                isLoading = false

                if (!snapshot.exists() || snapshot.childrenCount == 0L) {
                    if (isInitialLoad) {
                        tvEmptyState.visibility = View.VISIBLE
                    } else {
                        isLastPage = true
                    }
                    return
                }

                tvEmptyState.visibility = View.GONE

                var snapshotsList = snapshot.children.toList()
                oldestKey = snapshotsList.first().key

                if (!isInitialLoad && snapshotsList.isNotEmpty()) {
                    if (snapshotsList.last().key == logList.last().hashCode().toString()) {
                        snapshotsList = snapshotsList.dropLast(1)
                    }
                }

                if (snapshotsList.isEmpty()) {
                    isLastPage = true
                    return
                }

                if (snapshotsList.size < PAGE_SIZE - 1) {
                    isLastPage = true
                }

                val tempList = mutableListOf<String>()
                for (logSnapshot in snapshotsList) {
                    val logMessage = logSnapshot.getValue(String::class.java)
                    if (logMessage != null) {
                        tempList.add(logMessage)
                    } else {
                        val msgObj = logSnapshot.child("message").getValue(String::class.java)
                        if (msgObj != null) tempList.add(msgObj)
                    }
                }

                tempList.reverse()

                val startPosition = logList.size
                logList.addAll(tempList)

                if (isInitialLoad) {
                    logAdapter.notifyDataSetChanged()
                } else {
                    logAdapter.notifyItemRangeInserted(startPosition, tempList.size)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                isLoading = false
                progressBar.visibility = View.GONE
                Toast.makeText(this@UserLogsActivity, getString(R.string.error_loading_history), Toast.LENGTH_SHORT).show()
            }
        })
    }
}