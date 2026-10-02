package br.unisa.eventos.certificado;

import br.unisa.eventos.certificado.dto.DadosCertificado;
import br.unisa.eventos.shared.exception.RegraDeNegocioException;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
class GeradorCertificadoIText implements GeradorCertificado {

    private static final DateTimeFormatter DATA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale.Builder()
                    .setLanguage("pt").setRegion("BR").build());
    private static final DeviceRgb AZUL_UNISA = new DeviceRgb(20, 50, 110);
    private static final DeviceRgb CINZA = new DeviceRgb(90, 90, 90);

    @Override
    public byte[] gerar(DadosCertificado dados) {
        var saida = new ByteArrayOutputStream();

        try (PdfDocument pdf = new PdfDocument(new PdfWriter(saida));
             Document documento = new Document(pdf, PageSize.A4.rotate())) {

            documento.setMargins(60, 60, 50, 60);

            PdfFont titulo = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont corpo = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            documento.add(new Paragraph("CERTIFICADO")
                    .setFont(titulo).setFontSize(34).setFontColor(AZUL_UNISA)
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(4));

            documento.add(new Paragraph("Universidade Santo Amaro - UNISA")
                    .setFont(corpo).setFontSize(12).setFontColor(CINZA)
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(28));

            documento.add(new Paragraph("Certificamos que")
                    .setFont(corpo).setFontSize(13)
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(6));

            documento.add(new Paragraph(dados.nomeParticipante())
                    .setFont(titulo).setFontSize(24)
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(14));

            documento.add(new Paragraph(textoDoCorpo(dados))
                    .setFont(corpo).setFontSize(13)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setWidth(UnitValue.createPercentValue(85))
                    .setMarginLeft(40).setMarginRight(40).setMarginBottom(34)
                    .setMultipliedLeading(1.5f));

            documento.add(new Paragraph("Emitido em " + DATA.format(dados.dataEmissao()))
                    .setFont(corpo).setFontSize(11).setFontColor(CINZA)
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(16));

            documento.add(new Paragraph("Codigo de autenticidade: "
                    + dados.codigoAutenticidade())
                    .setFont(titulo).setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(2));

            documento.add(new Paragraph("Valide em " + dados.urlValidacao())
                    .setFont(corpo).setFontSize(10).setFontColor(CINZA)
                    .setTextAlignment(TextAlignment.CENTER));

        } catch (IOException e) {
            throw new RegraDeNegocioException("Nao foi possivel gerar o PDF do certificado.",
                    "FALHA_AO_GERAR_CERTIFICADO");
        }

        return saida.toByteArray();
    }

    private String textoDoCorpo(DadosCertificado dados) {
        String periodo = DATA.format(dados.dataInicio()).equals(DATA.format(dados.dataFim()))
                ? "em " + DATA.format(dados.dataInicio())
                : "no periodo de %s a %s".formatted(DATA.format(dados.dataInicio()),
                        DATA.format(dados.dataFim()));

        return "participou do evento \"%s\", realizado %s em %s, com carga horaria de %d hora(s)."
                .formatted(dados.tituloEvento(), periodo, dados.localEvento(),
                        dados.cargaHoraria());
    }
}
