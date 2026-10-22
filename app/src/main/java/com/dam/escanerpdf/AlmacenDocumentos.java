package com.dam.escanerpdf;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AlmacenDocumentos {

    private static final int LONGITUD_MAXIMA_NOMBRE = 100;

    private final Context contexto;
    private final File carpeta;

    public AlmacenDocumentos(Context contexto) {
        this.contexto = contexto.getApplicationContext();
        this.carpeta = new File(this.contexto.getFilesDir(), "documentos");
        if (!carpeta.exists()) {
            carpeta.mkdirs();
        }
    }

    public List<Documento> listar() {
        List<Documento> documentos = new ArrayList<>();
        File[] archivos = carpeta.listFiles((dir, nombre) -> nombre.toLowerCase(Locale.ROOT).endsWith(".pdf"));
        if (archivos != null) {
            for (File archivo : archivos) {
                documentos.add(new Documento(archivo));
            }
        }
        documentos.sort((a, b) -> Long.compare(b.getFecha(), a.getFecha()));
        return documentos;
    }

    public File guardarDesdeUri(Uri origen) throws IOException {
        String base = "Escaneo " + new SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.getDefault()).format(new Date());
        File destino = archivoDisponible(base);
        try (InputStream entrada = contexto.getContentResolver().openInputStream(origen);
             OutputStream salida = new FileOutputStream(destino)) {
            if (entrada == null) {
                throw new IOException("No se puede leer el archivo escaneado");
            }
            copiar(entrada, salida);
        } catch (IOException e) {
            destino.delete();
            throw e;
        }
        return destino;
    }

    public boolean renombrar(Documento documento, String nuevoNombre) {
        String limpio = limpiarNombre(nuevoNombre);
        if (limpio.isEmpty()) {
            return false;
        }
        File destino = new File(carpeta, limpio + ".pdf");
        if (destino.equals(documento.getArchivo())) {
            return true;
        }
        if (destino.exists()) {
            return false;
        }
        return documento.getArchivo().renameTo(destino);
    }

    public boolean eliminar(Documento documento) {
        return documento.getArchivo().delete();
    }

    public void exportar(Documento documento, Uri destino) throws IOException {
        try (InputStream entrada = new FileInputStream(documento.getArchivo());
             OutputStream salida = contexto.getContentResolver().openOutputStream(destino)) {
            if (salida == null) {
                throw new IOException("No se puede escribir en el destino elegido");
            }
            copiar(entrada, salida);
        }
    }

    private File archivoDisponible(String base) {
        File archivo = new File(carpeta, base + ".pdf");
        int numero = 2;
        while (archivo.exists()) {
            archivo = new File(carpeta, base + " (" + numero + ").pdf");
            numero++;
        }
        return archivo;
    }

    private String limpiarNombre(String nombre) {
        String limpio = nombre.trim();
        if (limpio.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            limpio = limpio.substring(0, limpio.length() - 4);
        }
        limpio = limpio.replaceAll("[\\\\/:*?\"<>|]", "").trim();
        if (limpio.length() > LONGITUD_MAXIMA_NOMBRE) {
            limpio = limpio.substring(0, LONGITUD_MAXIMA_NOMBRE).trim();
        }
        return limpio;
    }

    private void copiar(InputStream entrada, OutputStream salida) throws IOException {
        byte[] buffer = new byte[8192];
        int leidos;
        while ((leidos = entrada.read(buffer)) != -1) {
            salida.write(buffer, 0, leidos);
        }
    }
}
