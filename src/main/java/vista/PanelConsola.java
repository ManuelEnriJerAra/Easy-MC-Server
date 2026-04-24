package vista;

import controlador.GestorConfiguracion;
import controlador.GestorServidores;
import modelo.Server;
import com.formdev.flatlaf.extras.components.FlatCheckBox;
import com.formdev.flatlaf.extras.components.FlatScrollPane;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PanelConsola extends JPanel {
    private static final Pattern CHAT = Pattern.compile("<([^>]+)>\\s*(.*)");
    private static final Pattern JOIN = Pattern.compile("([^\\s]+) joined the game");
    private static final Pattern LEFT = Pattern.compile("([^\\s]+) left the game");
    private static final Pattern HORA = Pattern.compile("^\\[(\\d{2}:\\d{2}:\\d{2})]\\s*");

    // Colores por tipo
    private static final Color COLOR_INFO = AppTheme.getConsoleInfoForeground();
    private static final Color COLOR_CHAT = AppTheme.getConsoleChatForeground();
    private static final Color COLOR_ERROR = AppTheme.getConsoleErrorForeground();

    private final int MAX_LINEAS = 5000;

    private final JTextPane consolaPane = new JTextPane();
    private JTextPane comandoPane = new JTextPane();
    private final StyledDocument documento = consolaPane.getStyledDocument();

    private Style styleInfo;
    private Style styleChat;
    private Style styleError;

    private final Deque<String> rawLineas = new ArrayDeque<>();
    private final Set<String> jugadoresConectados = new LinkedHashSet<>();

    private final GestorServidores gestorServidores;
    private final FlatCheckBox vistaSimpleCheckbox = new FlatCheckBox();

    private int arc;
    private RoundedBackgroundPanel scrollWrap;
    private RoundedBackgroundPanel comandoWrap;

    public PanelConsola(GestorServidores gestorServidores) {
        this.setLayout(new BorderLayout(0, 8));
        this.setOpaque(false);
        this.gestorServidores = gestorServidores;
        this.setMinimumSize(new Dimension(this.getWidth(), 200));
        consolaPane.setEditable(false);
        this.setBorder(BorderFactory.createEmptyBorder());
        refreshThemeRefs();

        // Inicialización de estilo
        consolaPane.setBackground(AppTheme.getConsoleBackground());
        consolaPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
        consolaPane.setOpaque(true);
        inicializarEstilo();

        FlatScrollPane scroll = new FlatScrollPane();
        scroll.setViewportView(consolaPane);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        scroll.setWheelScrollingEnabled(true);
        scroll.getViewport().setOpaque(true);
        scroll.getViewport().setBackground(AppTheme.getConsoleBackground());

        vistaSimpleCheckbox.setText("Vista Simple");
        vistaSimpleCheckbox.setSelected(GestorConfiguracion.isConsolaVistaSimple());
        vistaSimpleCheckbox.addActionListener(e -> {
            GestorConfiguracion.guardarConsolaVistaSimple(vistaSimpleCheckbox.isSelected());
            actualizarConsola();
        });
        vistaSimpleCheckbox.setOpaque(false);
        vistaSimpleCheckbox.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        vistaSimpleCheckbox.setHorizontalTextPosition(SwingConstants.LEFT);
        vistaSimpleCheckbox.setForeground(AppTheme.getConsoleForeground());

        scrollWrap = new RoundedBackgroundPanel(AppTheme.getConsoleBackground(), arc);
        scrollWrap.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        scrollWrap.add(scroll, BorderLayout.CENTER);

        JPanel overlayBadge = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(AppTheme.withAlpha(AppTheme.getSelectionBackground(), 110));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                } finally {
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        overlayBadge.setOpaque(false);
        overlayBadge.add(vistaSimpleCheckbox, BorderLayout.CENTER);

        JPanel overlayHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        overlayHeader.setOpaque(false);
        overlayHeader.add(overlayBadge);

        JLayeredPane layeredConsole = new JLayeredPane(){
            @Override
            public void doLayout(){
                int w = getWidth();
                int h = getHeight();
                scrollWrap.setBounds(0, 0, w, h);

                Dimension pref = overlayHeader.getPreferredSize();
                int x = Math.max(0, w - pref.width);
                overlayHeader.setBounds(x, 0, pref.width, pref.height);
            }
        };
        layeredConsole.setOpaque(false);
        layeredConsole.add(scrollWrap, JLayeredPane.DEFAULT_LAYER);
        layeredConsole.add(overlayHeader, JLayeredPane.PALETTE_LAYER);
        this.add(layeredConsole, BorderLayout.CENTER);

        JPanel panelComandos = new JPanel(new BorderLayout(8, 0));
        panelComandos.setBackground(AppTheme.getConsoleBackground());
        panelComandos.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JLabel pico = new JLabel(">");
        pico.setForeground(AppTheme.getConsoleForeground());
        pico.setFont(new Font("Monospaced", Font.PLAIN, 12));
        comandoPane.setEditable(true);
        comandoPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
        comandoPane.setOpaque(false);
        comandoPane.setForeground(AppTheme.getConsoleForeground());
        comandoPane.setBorder(null);
        panelComandos.add(pico, BorderLayout.WEST);
        panelComandos.add(comandoPane, BorderLayout.CENTER);
        // Interacción con la línea de comandos
        comandoPane.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "enviarComando");
        comandoPane.getActionMap().put("enviarComando", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String comando = comandoPane.getText().trim();
                if(comando.isBlank()) return;
                Server server = gestorServidores.getServidorSeleccionado();
                gestorServidores.mandarComando(server, comando);
                comandoPane.setText("");
            }
        });
        comandoWrap = new RoundedBackgroundPanel(AppTheme.getConsoleBackground(), arc);
        comandoWrap.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        comandoWrap.add(panelComandos, BorderLayout.CENTER);

        this.add(comandoWrap, BorderLayout.SOUTH);

    }

    @Override
    public void updateUI(){
        super.updateUI();
        refreshThemeRefs();
        if(scrollWrap != null) scrollWrap.setArc(arc);
        if(comandoWrap != null) comandoWrap.setArc(arc);
    }

    private void refreshThemeRefs(){
        arc = AppTheme.getArc();
        Color consoleBackground = AppTheme.getConsoleBackground();
        if (consolaPane != null) {
            consolaPane.setBackground(consoleBackground);
        }
        if (scrollWrap != null) {
            scrollWrap.setBackground(consoleBackground);
        }
        if (comandoWrap != null) {
            comandoWrap.setBackground(consoleBackground);
        }
    }

    private void inicializarEstilo() {
        styleInfo = documento.addStyle("INFO", null);
        StyleConstants.setForeground(styleInfo, COLOR_INFO);

        styleChat = documento.addStyle("CHAT", null);
        StyleConstants.setForeground(styleChat, COLOR_CHAT);

        styleError = documento.addStyle("ERROR", null);
        StyleConstants.setForeground(styleError, COLOR_ERROR);
    }

    void actualizarConsola(){
        Server server = gestorServidores.getServidorSeleccionado();
        if(server == null) return;

        rawLineas.clear();
        rawLineas.addAll(server.getRawLogLines());
        jugadoresConectados.clear();

        reconstruirDocumentoCompleto();
    }

    public void escribirLinea(String raw){
        SwingUtilities.invokeLater(()->{
            if(raw.isBlank()) return;

            rawLineas.addLast(raw);

            boolean recortado = false;
            while(rawLineas.size()>MAX_LINEAS){
                rawLineas.removeFirst();
                recortado = true;
            }

            if(recortado){
                reconstruirDocumentoCompleto();
                return;
            }

            boolean vistaSimple = vistaSimpleCheckbox.isSelected();
            RenderLine renderLine = render(raw, vistaSimple);
            actualizarJugadoresConectados(raw);

            // si no hay traducción no imprimimos nada
            if(vistaSimple && renderLine.texto==null) return;

            appendStyledLine(renderLine.texto, renderLine.estilo);
        });
    }

    private void reconstruirDocumentoCompleto(){
        try{
            documento.remove(0, documento.getLength());
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
        boolean vistaSimple = vistaSimpleCheckbox.isSelected();
        jugadoresConectados.clear();
        for(String raw : rawLineas){
            RenderLine renderLine = render(raw, vistaSimple);
            actualizarJugadoresConectados(raw);

            if(vistaSimple && renderLine.texto==null) continue;

            appendStyledLine(renderLine.texto, renderLine.estilo);
        }
    }

    private void appendStyledLine(String texto, Style estilo){
        if (texto.isEmpty()) return;
        try{
            documento.insertString(documento.getLength(), texto + "\n", estilo);
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
        consolaPane.setCaretPosition(documento.getLength());
    }

    private RenderLine render(String raw, boolean vistaSimple){
        if(vistaSimple){
            String traducida = traducirLinea(raw); // puede devolver null
            if(traducida == null) return new RenderLine(null, styleInfo); // no se muestra
            return new RenderLine(traducida, estiloPorTexto(traducida));
        } else {
            return new RenderLine(raw, styleInfo);
        }
    }

    private Style estiloPorTexto(String texto){
        // Errores
        if (texto.contains("[ERROR]")) return styleError;

        // chat
        if (texto.contains("[CHAT]")) return styleChat;

        // info
        return  styleInfo;
    }

    private String traducirLinea(String linea){
        // ===== TRADUCCIÓN DE EVENTOS CONOCIDOS =====
        // vamos a quitarle la hora primero
        String hora = extraerHora(linea);


        if (linea.contains("[INFO]") || linea.contains("[CHAT]") || linea.contains("[ERROR]")) {
            return linea; // es una línea que yo he escrito, que pase
        }

        // Errores
        if (linea.contains("FAILED TO BIND TO PORT")){
            return hora+"[ERROR] El puerto ya está en uso, es probable que haya otro servidor abierto.";
        }

        if(linea.contains("Done")){
            return(hora+"[INFO] El servidor se ha iniciado exitosamente.");
        }
        if(linea.contains("All dimensions are saved")){
            return(hora+"[INFO] Mundo guardado.");
        }

        // Chat
        Matcher mChat = CHAT.matcher(linea);
        if(mChat.find()){
            String user = mChat.group(1);
            if(user != null) user = user.trim();
            if(user == null || user.isBlank() || !jugadoresConectados.contains(user)) return null;
            String mensaje = mChat.group(2);
            mensaje = mensaje.replaceAll("§.", "").trim();
            if(mensaje.isBlank()) return null;
            return (hora+"[CHAT] " + user + " ha dicho: " + mensaje);
        }

        // Join / Leave
        Matcher mJoin = JOIN.matcher(linea);
        if(mJoin.find()){
            return(hora+"[INFO] "+mJoin.group(1)+" ha entrado.");
        }
        Matcher mLeft = LEFT.matcher(linea);
        if(mLeft.find()){
            return (hora+"[INFO] "+mLeft.group(1)+" ha salido.");
        }
        return null;
    }

    private void actualizarJugadoresConectados(String linea){
        if(linea == null || linea.isBlank()) return;

        Matcher mJoin = JOIN.matcher(linea);
        if(mJoin.find()){
            String nombre = mJoin.group(1);
            if(nombre != null){
                nombre = nombre.trim();
                if(!nombre.isBlank()){
                    jugadoresConectados.add(nombre);
                }
            }
            return;
        }

        Matcher mLeft = LEFT.matcher(linea);
        if(mLeft.find()){
            String nombre = mLeft.group(1);
            if(nombre != null){
                nombre = nombre.trim();
                if(!nombre.isBlank()){
                    jugadoresConectados.remove(nombre);
                }
            }
        }
    }
    private static class RenderLine {
        final String texto;
        final Style estilo;
        RenderLine(String texto, Style estilo) {
            this.texto = texto;
            this.estilo = estilo;
        }
    }

    private String extraerHora(String linea){
        if(linea == null) return null;
        Matcher mHora = HORA.matcher(linea);
        if(mHora.find()){
            return "["+mHora.group(1)+"] ";
        }
        return null;
    }
}

