package com.dam.escanerpdf;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.IOException;

public final class Previsualizador {

    public static class Resultado {
        public final Bitmap miniatura;
        public final int paginas;

        Resultado(Bitmap miniatura, int paginas) {
            this.miniatura = miniatura;
            this.paginas = paginas;
        }
    }

    private Previsualizador() {
    }

    public static Resultado generar(File pdf, int anchoPx) throws IOException {
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)) {
            PdfRenderer renderer = new PdfRenderer(descriptor);
            try {
                int paginas = renderer.getPageCount();
                PdfRenderer.Page pagina = renderer.openPage(0);
                try {
                    int alto = Math.max(1, anchoPx * pagina.getHeight() / pagina.getWidth());
                    Bitmap bitmap = Bitmap.createBitmap(anchoPx, alto, Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(Color.WHITE);
                    pagina.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    return new Resultado(bitmap, paginas);
                } finally {
                    pagina.close();
                }
            } finally {
                renderer.close();
            }
        }
    }
}
