package com.dam.escanerpdf;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;

public class MainActivity extends AppCompatActivity implements DocumentoAdapter.OnDocumentoListener {

    private static final int PAGINAS_MAXIMAS = 25;

    private DocumentosViewModel viewModel;
    private DocumentoAdapter adapter;
    private GmsDocumentScanner escaner;
    private TextView txtVacio;

    private final ActivityResultLauncher<IntentSenderRequest> lanzadorEscaner =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(),
                    this::alRecibirEscaneo);

    private final ActivityResultLauncher<String> lanzadorGuardar =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/pdf"),
                    this::alElegirDestino);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtVacio = findViewById(R.id.txtVacio);
        RecyclerView recycler = findViewById(R.id.recyclerDocumentos);
        ExtendedFloatingActionButton fab = findViewById(R.id.fabEscanear);

        viewModel = new ViewModelProvider(this).get(DocumentosViewModel.class);

        adapter = new DocumentoAdapter(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        GmsDocumentScannerOptions opciones = new GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(PAGINAS_MAXIMAS)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build();
        escaner = GmsDocumentScanning.getClient(opciones);

        fab.setOnClickListener(v -> iniciarEscaneo());

        viewModel.getDocumentos().observe(this, documentos -> {
            adapter.submitList(documentos);
            txtVacio.setVisibility(documentos.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onDocumentoClick(Documento documento) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uriDe(documento), "application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            mostrarMensaje(R.string.sin_visor);
        }
    }

    @Override
    public void onOpcionesClick(Documento documento, View ancla) {
        PopupMenu menu = new PopupMenu(this, ancla);
        menu.inflate(R.menu.menu_documento);
        menu.setOnMenuItemClickListener(opcion -> {
            int id = opcion.getItemId();
            if (id == R.id.accionCompartir) {
                compartir(documento);
            } else if (id == R.id.accionGuardar) {
                viewModel.setPendienteDeExportar(documento);
                lanzadorGuardar.launch(documento.getArchivo().getName());
            } else if (id == R.id.accionRenombrar) {
                pedirNuevoNombre(documento);
            } else if (id == R.id.accionEliminar) {
                confirmarEliminar(documento);
            }
            return true;
        });
        menu.show();
    }

    private void iniciarEscaneo() {
        escaner.getStartScanIntent(this)
                .addOnSuccessListener(intentSender ->
                        lanzadorEscaner.launch(new IntentSenderRequest.Builder(intentSender).build()))
                .addOnFailureListener(e -> mostrarMensaje(R.string.error_escaner));
    }

    private void alRecibirEscaneo(ActivityResult resultado) {
        if (resultado.getResultCode() != RESULT_OK) {
            return;
        }
        GmsDocumentScanningResult escaneo =
                GmsDocumentScanningResult.fromActivityResultIntent(resultado.getData());
        if (escaneo == null || escaneo.getPdf() == null) {
            mostrarMensaje(R.string.error_guardar);
            return;
        }
        viewModel.guardarEscaneo(escaneo.getPdf().getUri(), correcto ->
                mostrarMensaje(correcto ? R.string.documento_guardado : R.string.error_guardar));
    }

    private void alElegirDestino(Uri destino) {
        if (destino == null) {
            return;
        }
        viewModel.exportar(destino, correcto ->
                mostrarMensaje(correcto ? R.string.guardado_en_movil : R.string.error_guardar));
    }

    private void compartir(Documento documento) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, uriDe(documento));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, getString(R.string.compartir)));
    }

    private void pedirNuevoNombre(Documento documento) {
        View vista = getLayoutInflater().inflate(R.layout.dialog_renombrar, null);
        TextInputEditText campo = vista.findViewById(R.id.inputNombre);
        campo.setText(documento.getNombre());
        campo.selectAll();

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.renombrar)
                .setView(vista)
                .setNegativeButton(R.string.cancelar, null)
                .setPositiveButton(R.string.aceptar, (dialogo, which) -> {
                    String nombre = campo.getText() == null ? "" : campo.getText().toString();
                    viewModel.renombrar(documento, nombre, correcto -> {
                        if (!correcto) {
                            mostrarMensaje(R.string.error_renombrar);
                        }
                    });
                })
                .show();
    }

    private void confirmarEliminar(Documento documento) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.eliminar_documento)
                .setMessage(getString(R.string.confirmar_eliminar_mensaje, documento.getNombre()))
                .setNegativeButton(R.string.cancelar, null)
                .setPositiveButton(R.string.eliminar, (dialogo, which) ->
                        viewModel.eliminar(documento, correcto ->
                                mostrarMensaje(correcto ? R.string.documento_eliminado : R.string.error_guardar)))
                .show();
    }

    private Uri uriDe(Documento documento) {
        return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", documento.getArchivo());
    }

    private void mostrarMensaje(int recurso) {
        Snackbar.make(findViewById(android.R.id.content), recurso, Snackbar.LENGTH_LONG).show();
    }
}
