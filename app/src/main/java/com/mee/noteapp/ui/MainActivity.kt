package com.mee.noteapp.ui

import android.graphics.PorterDuff
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.mee.noteapp.R
import com.mee.noteapp.ui.fragments.PagesFragment
import com.mee.noteapp.ui.fragments.PdfFragment

/**
 * Pantalla principal: barra de navegación inferior (hecha a mano con LinearLayouts, sin
 * BottomNavigationView) con dos secciones — "Páginas" (MHTML empaquetadas) y "PDF"
 * (abrir/leer documentos del dispositivo). La barra superior tiene accesos a las notas
 * generales (popup) y al cambio de tema claro/oscuro.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var navPages: LinearLayout
    private lateinit var navPdf: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))

        navPages = findViewById(R.id.navPages)
        navPdf = findViewById(R.id.navPdf)

        if (savedInstanceState == null) {
            showFragment(PagesFragment())
            selectTab(navPages)
        }

        navPages.setOnClickListener { showFragment(PagesFragment()); selectTab(navPages) }
        navPdf.setOnClickListener { showFragment(PdfFragment()); selectTab(navPdf) }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_general_notes -> showGeneralNotesDialog(this)
            R.id.action_toggle_theme -> {
                ThemePrefs.toggle(this)
                recreate()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun selectTab(selected: LinearLayout) {
        val activeColor = ContextCompat.getColor(this, R.color.brand_primary)
        val inactiveColor = ContextCompat.getColor(this, R.color.brand_text_muted)
        for (tab in listOf(navPages, navPdf)) {
            val color = if (tab == selected) activeColor else inactiveColor
            (tab.getChildAt(0) as ImageView).setColorFilter(color, PorterDuff.Mode.SRC_IN)
            (tab.getChildAt(1) as TextView).setTextColor(color)
        }
    }
}
