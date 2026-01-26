package Vista;

import Controlador.GestorServidores;
import Modelo.Server;
import com.formdev.flatlaf.extras.components.FlatCheckBox;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PanelConsola extends JPanel {
    private static final Pattern CHAT = Pattern.compile("<([^>]+)>\\s*(.*)");
    private static final Pattern JOIN = Pattern.compile("([^\\s]+) joined the game");
    private static final Pattern LEFT = Pattern.compile("([^\\s]+) left the game");
    private static final Pattern HORA = Pattern.compile("^\\[(\\d{2}:\\d{2}:\\d{2})]\\s*");


    private final Color colorConsola = Color.decode("#1D2036");

    // Colores por tipo
    private static final Color COLOR_INFO = Color.WHITE;
    private static final Color COLOR_CHAT = Color.CYAN;
    private static final Color COLOR_ERROR = Color.RED;

    private final int MAX_LINEAS = 5000;

    private JTextPane consolaPane = new JTextPane();
    private final StyledDocument documento = consolaPane.getStyledDocument();

    private Style styleInfo;
    private Style styleChat;
    private Style styleError;

    private final Deque<String> rawLineas = new ArrayDeque<>();

    private final GestorServidores gestorServidores;
    private final FlatCheckBox vistaSimpleCheckbox = new FlatCheckBox();


    public PanelConsola(GestorServidores gestorServidores) {
        this.setLayout(new BorderLayout());
        this.gestorServidores = gestorServidores;

        consolaPane.setEditable(false);

        // Inicialización de estilo
        consolaPane.setBackground(colorConsola);
        consolaPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
        consolaPane.setOpaque(true);
        inicializarEstilo();

        JScrollPane scroll = new JScrollPane(consolaPane);
        this.add(scroll, BorderLayout.CENTER);

        vistaSimpleCheckbox.setText("Vista Simple");
        vistaSimpleCheckbox.setSelected(true);
        vistaSimpleCheckbox.addActionListener(e->actualizarConsola());

        this.add(vistaSimpleCheckbox, BorderLayout.NORTH);

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
        for(String raw : rawLineas){
            RenderLine renderLine = render(raw, vistaSimple);

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
