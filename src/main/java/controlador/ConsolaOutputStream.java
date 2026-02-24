package controlador;

import vista.PanelConsola;

import java.io.OutputStream;

public class ConsolaOutputStream extends OutputStream {
    private final PanelConsola panelConsola;
    private final StringBuilder buffer = new StringBuilder();
    public ConsolaOutputStream(PanelConsola panelConsola) {
        this.panelConsola = panelConsola;
    }

    @Override
    public void write(int b){
        // convertimos cada byte a char y lo escribimos
        char c = (char)b;
        if(c == '\r') return;
        if(c == '\n'){
            flushLine(); // si terminamos la línea la mandamos
        } else {
            buffer.append(c); // seguimos construyendo
        }
    }

    @Override
    public void write(byte[] b, int off, int len){
        for(int i = off; i < off + len; i++){
            write(b[i]);
        }
    }

    private void flushLine(){
        String linea = buffer.toString();
        buffer.setLength(0);
        panelConsola.escribirLinea(linea);
    }

    @Override
    public void flush(){
        if(!buffer.isEmpty()) flushLine();
    }
}
