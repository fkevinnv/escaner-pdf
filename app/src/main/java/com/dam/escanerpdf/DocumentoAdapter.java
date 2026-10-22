package com.dam.escanerpdf;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DocumentoAdapter extends ListAdapter<Documento, DocumentoAdapter.DocumentoViewHolder> {

    public interface OnDocumentoListener {
        void onDocumentoClick(Documento documento);

        void onOpcionesClick(Documento documento, View ancla);
    }

    private static final int ANCHO_MINIATURA_PX = 160;

    private static final DiffUtil.ItemCallback<Documento> DIFF = new DiffUtil.ItemCallback<Documento>() {
        @Override
        public boolean areItemsTheSame(@NonNull Documento anterior, @NonNull Documento nuevo) {
            return anterior.getArchivo().equals(nuevo.getArchivo());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Documento anterior, @NonNull Documento nuevo) {
            return anterior.getFecha() == nuevo.getFecha() && anterior.getTamanio() == nuevo.getTamanio();
        }
    };

    private final OnDocumentoListener listener;
    private final ExecutorService ejecutor = Executors.newSingleThreadExecutor();
    private final Handler hiloPrincipal = new Handler(Looper.getMainLooper());
    private final LruCache<String, Previsualizador.Resultado> cache = new LruCache<>(60);

    public DocumentoAdapter(OnDocumentoListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public DocumentoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View vista = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_documento, parent, false);
        return new DocumentoViewHolder(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull DocumentoViewHolder holder, int posicion) {
        Documento documento = getItem(posicion);
        Context contexto = holder.itemView.getContext();
        String clave = documento.getArchivo().getAbsolutePath() + documento.getFecha();

        holder.txtNombre.setText(documento.getNombre());
        holder.imgMiniatura.setTag(clave);
        holder.itemView.setOnClickListener(v -> listener.onDocumentoClick(documento));
        holder.btnOpciones.setOnClickListener(v -> listener.onOpcionesClick(documento, v));

        Previsualizador.Resultado guardado = cache.get(clave);
        if (guardado != null) {
            mostrar(holder, documento, guardado);
            return;
        }

        holder.imgMiniatura.setImageDrawable(null);
        holder.txtDetalle.setText(detalle(contexto, documento, -1));
        ejecutor.execute(() -> {
            try {
                Previsualizador.Resultado resultado =
                        Previsualizador.generar(documento.getArchivo(), ANCHO_MINIATURA_PX);
                cache.put(clave, resultado);
                hiloPrincipal.post(() -> {
                    if (clave.equals(holder.imgMiniatura.getTag())) {
                        mostrar(holder, documento, resultado);
                    }
                });
            } catch (Exception e) {
                // Si no se puede generar la miniatura se deja el hueco vacio.
            }
        });
    }

    private void mostrar(DocumentoViewHolder holder, Documento documento, Previsualizador.Resultado resultado) {
        holder.imgMiniatura.setImageBitmap(resultado.miniatura);
        holder.txtDetalle.setText(detalle(holder.itemView.getContext(), documento, resultado.paginas));
    }

    private String detalle(Context contexto, Documento documento, int paginas) {
        String tamanio = Formatter.formatShortFileSize(contexto, documento.getTamanio());
        String fecha = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(documento.getFecha()));
        if (paginas < 0) {
            return tamanio + " · " + fecha;
        }
        String textoPaginas = contexto.getResources().getQuantityString(R.plurals.paginas, paginas, paginas);
        return textoPaginas + " · " + tamanio + " · " + fecha;
    }

    static class DocumentoViewHolder extends RecyclerView.ViewHolder {
        final ImageView imgMiniatura;
        final TextView txtNombre;
        final TextView txtDetalle;
        final ImageButton btnOpciones;

        DocumentoViewHolder(@NonNull View vista) {
            super(vista);
            imgMiniatura = vista.findViewById(R.id.imgMiniatura);
            txtNombre = vista.findViewById(R.id.txtNombre);
            txtDetalle = vista.findViewById(R.id.txtDetalle);
            btnOpciones = vista.findViewById(R.id.btnOpciones);
        }
    }
}
