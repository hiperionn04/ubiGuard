package pt.fct.unl.sim.ubiguard

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
import com.google.firebase.database.*

class InstallerLogsActivity : BaseActivity() {

    private val logList = mutableListOf<String>()
    private lateinit var logAdapter: LogAdapter
    private lateinit var database: DatabaseReference

    // ==========================================
    // VARIÁVEIS DO INFINITE SCROLLING
    // ==========================================
    private var currentAlarmId: String = ""
    private var isLoading = false    // Evita que ele faça 10 pedidos ao mesmo tempo se a pessoa fizer muito scroll
    private var isLastPage = false   // Diz-nos quando chegámos ao fim do histórico
    private var oldestKey: String? = null // O nosso "Cursor" (A chave do log mais antigo que já vimos)
    private val PAGE_SIZE = 50       // Quantos logs carrega de cada vez

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

        database = FirebaseDatabase.getInstance().reference

        // 1. O clique no botão inicia a PRIMEIRA página
        btnSearch.setOnClickListener {
            val alarmId = etAlarmId.text.toString().trim()

            if (alarmId.isEmpty()) {
                Toast.makeText(this, "Por favor insere um ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Reset a todas as variáveis de paginação porque é uma pesquisa nova
            currentAlarmId = alarmId
            isLastPage = false
            oldestKey = null

            carregarLogs(isInitialLoad = true)
        }

        // 2. O DETETOR DE SCROLL (A verdadeira magia do Infinite Scroll)
        rvLogs.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                // Se estivermos a ir para baixo (dy > 0)
                if (dy > 0) {
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                    // Se não estiver a carregar nada agora, e não chegámos ao fim do histórico...
                    if (!isLoading && !isLastPage) {
                        // Se chegámos ao fundo do ecrã
                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount && firstVisibleItemPosition >= 0) {
                            carregarLogs(isInitialLoad = false) // Carrega a PRÓXIMA página!
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

    // ==========================================
    // FUNÇÃO QUE CARREGA PÁGINAS (INICIAIS E SEGUINTES)
    // ==========================================
    private fun carregarLogs(isInitialLoad: Boolean) {
        if (isLoading) return
        isLoading = true
        progressBar.visibility = View.VISIBLE

        if (isInitialLoad) {
            logList.clear()
            logAdapter.notifyDataSetChanged()
            rvLogs.visibility = View.GONE
            tvEmptyState.visibility = View.GONE
        }

        // Prepara a pergunta à base de dados
        var query = database.child("alarms").child(currentAlarmId).child("logs").orderByKey()

        if (isInitialLoad) {
            // Se for a 1ª página, pede só os últimos 50
            query = query.limitToLast(PAGE_SIZE)
        } else {
            // Se for as páginas seguintes, pede os 50 terminando no log mais antigo que já tínhamos visto
            query = query.endAt(oldestKey).limitToLast(PAGE_SIZE)
        }

        // Ouve apenas UMA vez (não é tempo real, para poupar memória)
        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                progressBar.visibility = View.GONE
                isLoading = false

                if (!snapshot.exists() || snapshot.childrenCount == 0L) {
                    if (isInitialLoad) {
                        tvEmptyState.text = "Nenhum log encontrado para este Alarme."
                        tvEmptyState.visibility = View.VISIBLE
                    } else {
                        isLastPage = true // Chegámos ao fim do histórico
                    }
                    return
                }

                tvEmptyState.visibility = View.GONE
                rvLogs.visibility = View.VISIBLE

                var snapshotsList = snapshot.children.toList()

                // Como a Firebase devolve os itens ordenados cronologicamente (o mais antigo no topo),
                // o primeiro elemento desta lista será a nossa nova "oldestKey" para a próxima página
                oldestKey = snapshotsList.first().key

                // TRUQUE DE MESTRE: Se não for a carga inicial, o Firebase devolve-nos o "oldestKey" antigo
                // outra vez na cauda do array (porque usámos endAt). Temos de o deitar fora para não haver duplicados!
                if (!isInitialLoad && snapshotsList.isNotEmpty()) {
                    if (snapshotsList.last().key == logList.last().hashCode().toString() /* Aproximação, mas a Firebase faz isto */) {
                        // O verdadeiro truque: apenas deitamos fora a última posição
                        snapshotsList = snapshotsList.dropLast(1)
                    }
                }

                // Se depois de deitar fora a cópia não sobrar nada, é porque a BD acabou
                if (snapshotsList.isEmpty()) {
                    isLastPage = true
                    return
                }

                // Se a base de dados nos devolveu menos itens do que pedimos, também significa que acabou
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

                // Inverte para o mais recente ficar em cima
                tempList.reverse()

                // Adiciona os novos logs ao fim da nossa lista visual
                val startPosition = logList.size
                logList.addAll(tempList)

                if (isInitialLoad) {
                    logAdapter.notifyDataSetChanged()
                } else {
                    // Diz ao ecrã para desenhar as novas linhas sem piscar o resto do ecrã
                    logAdapter.notifyItemRangeInserted(startPosition, tempList.size)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                isLoading = false
                progressBar.visibility = View.GONE
                Toast.makeText(this@InstallerLogsActivity, "Erro ao carregar logs.", Toast.LENGTH_SHORT).show()
            }
        })
    }
}