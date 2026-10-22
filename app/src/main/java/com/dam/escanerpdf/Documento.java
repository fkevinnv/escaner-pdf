package com.dam.escanerpdf;

import java.io.File;

public class Documento {

    private final File archivo;
    private final long fecha;
    private final long tamanio;

    public Documento(File archivo) {
        this.archivo = archivo;
        this.fecha = archivo.lastModified();
        this.tamanio = archivo.length();
    }

    public File getArchivo() {
        return archivo;
    }

    public String getNombre() {
        String nombre = archivo.getName();
        int punto = nombre.lastIndexOf('.');
        return punto > 0 ? nombre.substring(0, punto) : nombre;
    }

    public long getFecha() {
        return fecha;
    }

    public long getTamanio() {
        return tamanio;
    }
}
