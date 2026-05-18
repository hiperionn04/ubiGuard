package pt.fct.unl.sim.ubiguard.ui.alarm

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import pt.fct.unl.sim.ubiguard.UbiGuardApp
import com.google.firebase.database.ValueEventListener
import pt.fct.unl.sim.ubiguard.R
import pt.fct.unl.sim.ubiguard.adapters.LogAdapter
import pt.fct.unl.sim.ubiguard.ui.base.BaseActivity

class InstallerLogsActivity : BaseActivity() {

    private val logList = mutableListOf<String>()
    private lateinit var logAdapter: LogAdapter
    private lateinit var database: DatabaseReference

    private var currentAlarmId: String = ""
    private var isLoading = false
    private var isLastPage = false
    private var oldestKey: String? = null
    private val PAGE_SIZE = 50

    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var rvLogs: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_installer_logs)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val ivMenuIcon = findViewById<ImageView>(R.id.ivMenuIcon)
        ativarSliderComponent(drawerLayout, ivMenuIcon)

        val etAlarmId = findViewById<EditText>(R.id.etAlarmId)
        val btnSearch = findViewById<AppCompatButton>(R.id.btnSearchLogs)

        rvLogs = findViewById(R.id.rvLogs)
        progressBar = findViewById(R.id.progressBarLogs)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        val layoutManager = LinearLayoutManager(this)
        rvLogs.layoutManager = layoutManager
        logAdapter = LogAdapter(logList)
        rvLogs.adapter = logAdapter

        database = FirebaseDatabase.getInstance(UbiGuardApp.DATABASE_URL).reference

        btnSearch.setOnClickListener {
            val alarmId = etAlarmId.text.toString().trim()

            if (alarmId.isEmpty()) {
                Toast.makeText(this, getString(R.string.general_insert_id), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            currentAlarmId = alarmId
            isLastPage = false
            oldestKey = null

            loadLogs(isInitialLoad = true)
        }

        rvLogs.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                if (dy > 0) {
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    if (!isLoading && !isLastPage) {
                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount && firstVisibleItemPosition >= 0) {
                            loadLogs(isInitialLoad = false)
                        }
                    }
                }
            }
        })

        val prefillId = intent.getStringExtra("ALARM_ID_PREFILL")
        if (prefillId != null) {
            etAlarmId.setText(prefillId)
            btnSearch.performClick()
        }
    }

    /**
     * Loads pages
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun loadLogs(isInitialLoad: Boolean) {
        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE

        if (isInitialLoad) {
            logList.clear()
            logAdapter.notifyDataSetChanged()
            rvLogs.visibility = View.GONE
            tvEmptyState.visibility = View.GONE
        }

        var query = database.child("alarms").child(currentAlarmId).child("logs").orderByKey()

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
                        tvEmptyState.text = getString(R.string.general_no_log)
                        tvEmptyState.visibility = View.VISIBLE
                    } else {
                        isLastPage = true
                    }
                    return
                }

                tvEmptyState.visibility = View.GONE
                rvLogs.visibility = View.VISIBLE

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
                Toast.makeText(this@InstallerLogsActivity, getString(R.string.error_loading_logs), Toast.LENGTH_SHORT).show()
            }
        })
    }
}