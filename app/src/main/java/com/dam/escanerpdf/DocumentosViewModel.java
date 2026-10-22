package com.dam.escanerpdf;

import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class DocumentosViewModel extends AndroidViewModel {

    private final AlmacenDocumentos almacen;
    private final ExecutorService ejecutor = Executors.newSingleThreadExecutor();
    private final Handler hiloPrincipal = new Handler(Looper.getMainLooper());
    private final MutableLiveData<List<Documento>> documentos = new MutableLiveData<>();

    private Documento pendienteDeExportar;

    public DocumentosViewModel(@NonNull Application application) {
        super(application);
        almacen = new AlmacenDocumentos(application);
        ejecutor.execute(() -> documentos.postValue(almacen.listar()));
    }

    public LiveData<List<Documento>> getDocumentos() {
        return documentos;
    }

    public void setPendienteDeExportar(Documento documento) {
        pendienteDeExportar = documento;
    }

    public void guardarEscaneo(Uri origen, Consumer<Boolean> alTerminar) {
        ejecutar(() -> almacen.guardarDesdeUri(origen) != null, alTerminar);
    }

    public void renombrar(Documento documento, String nuevoNombre, Consumer<Boolean> alTerminar) {
        ejecutar(() -> almacen.renombrar(documento, nuevoNombre), alTerminar);
    }

    public void eliminar(Documento documento, Consumer<Boolean> alTerminar) {
        ejecutar(() -> almacen.eliminar(documento), alTerminar);
    }

    public void exportar(Uri destino, Consumer<Boolean> alTerminar) {
        Documento documento = pendienteDeExportar;
        ejecutar(() -> {
            if (documento == null) {
                return false;
            }
            almacen.exportar(documento, destino);
            return true;
        }, alTerminar);
    }

    private void ejecutar(Callable<Boolean> tarea, Consumer<Boolean> alTerminar) {
        ejecutor.execute(() -> {
            boolean correcto;
            try {
                correcto = tarea.call();
            } catch (Exception e) {
                correcto = false;
            }
            documentos.postValue(almacen.listar());
            boolean resultado = correcto;
            hiloPrincipal.post(() -> alTerminar.accept(resultado));
        });
    }

    @Override
    protected void onCleared() {
        ejecutor.shutdown();
    }
}
