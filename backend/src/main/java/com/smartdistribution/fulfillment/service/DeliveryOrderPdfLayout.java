package com.smartdistribution.fulfillment.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.smartdistribution.document.pdf.PdfRenderer;
import com.smartdistribution.fulfillment.entity.DeliveryOrder;
import com.smartdistribution.fulfillment.entity.DeliveryOrderLine;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.customer.entity.Customer;
import com.smartdistribution.sales.entity.DeliveryMethod;
import com.smartdistribution.sales.service.SalesOrderForDelivery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 出貨單的版面：三聯單，一份 PDF 三頁，內容相同，只有右上角的聯別不同
 * 只列實際量大於 0 的明細，不含任何價格
 */
@Component
@RequiredArgsConstructor
public class DeliveryOrderPdfLayout {

    private static final List<String> COPIES = List.of(
            "第一聯　存根聯（公司留存）",
            "第二聯　客戶聯（交給客戶）",
            "第三聯　簽收聯（簽名後由司機帶回）"
    );

    private final PdfRenderer pdfRenderer;

    /**
     * @param items 料號 ID → 公司料號，用來印料號與品名
     */
    public byte[] render(
            DeliveryOrder deliveryOrder,
            SalesOrderForDelivery salesOrder,
            Customer customer,
            List<DeliveryOrderLine> lines,
            Map<Long, CompanyItem> items
    ) {
        List<DeliveryOrderLine> shippedLines = lines.stream().filter(DeliveryOrderLine::isShipped).toList();

        return pdfRenderer.render(document -> {
            for (int index = 0; index < COPIES.size(); index++) {
                if (index > 0) {
                    document.newPage();
                }
                writeCopy(document, COPIES.get(index), deliveryOrder, salesOrder, customer, shippedLines, items);
            }
        });
    }

    private void writeCopy(
            Document document,
            String copyLabel,
            DeliveryOrder deliveryOrder,
            SalesOrderForDelivery salesOrder,
            Customer customer,
            List<DeliveryOrderLine> lines,
            Map<Long, CompanyItem> items
    ) throws Exception {
        Font title = pdfRenderer.boldFont(18);
        Font text = pdfRenderer.font(10);
        Font header = pdfRenderer.boldFont(10);

        Paragraph label = pdfRenderer.paragraph(copyLabel, text);
        label.setAlignment(Element.ALIGN_RIGHT);
        document.add(label);

        Paragraph heading = pdfRenderer.paragraph("出貨單", title);
        heading.setAlignment(Element.ALIGN_CENTER);
        document.add(heading);

        document.add(pdfRenderer.paragraph(
                "出貨單號：" + deliveryOrder.getDoNumber()
                        + "　　訂單編號：" + salesOrder.soNumber()
                        + "　　客戶訂單號：" + orDash(salesOrder.customerOrderNumber()),
                text
        ));
        document.add(pdfRenderer.paragraph(
                "客戶：" + customer.getCustomerCode() + " " + customer.getName(),
                text
        ));
        document.add(pdfRenderer.paragraph(
                "交貨方式：" + deliveryMethodName(salesOrder.deliveryMethod())
                        + "　　預計出貨日：" + deliveryOrder.getPlannedShipmentDate(),
                text
        ));
        document.add(pdfRenderer.paragraph("交貨地址：" + orDash(salesOrder.deliveryAddress()), text));
        document.add(pdfRenderer.paragraph(
                "收貨聯絡人：" + orDash(salesOrder.contactName()) + "　　電話：" + orDash(salesOrder.contactPhone()),
                text
        ));

        document.add(linesTable(lines, salesOrder, items, header, text));

        if (deliveryOrder.getRemark() != null) {
            document.add(pdfRenderer.paragraph("備註：" + deliveryOrder.getRemark(), text));
        }

        document.add(pdfRenderer.paragraph(" ", text));
        document.add(pdfRenderer.paragraph("實際出貨日：＿＿＿＿＿＿＿＿　　司機：＿＿＿＿＿＿＿＿", text));
        document.add(pdfRenderer.paragraph(" ", text));
        document.add(pdfRenderer.paragraph("收貨人簽名：＿＿＿＿＿＿＿＿　　簽收日期：＿＿＿＿＿＿＿＿", text));
        document.add(pdfRenderer.paragraph(" ", text));
        document.add(pdfRenderer.paragraph("本出貨單由系統產生。", pdfRenderer.font(8)));
    }

    private PdfPTable linesTable(
            List<DeliveryOrderLine> lines,
            SalesOrderForDelivery salesOrder,
            Map<Long, CompanyItem> items,
            Font header,
            Font text
    ) {
        Map<Long, SalesOrderForDelivery.Line> salesOrderLines = salesOrder.lines()
                .stream()
                .collect(Collectors.toMap(SalesOrderForDelivery.Line::id, Function.identity()));
        PdfPTable table = pdfRenderer.table(new float[]{0.6f, 1.6f, 3f, 1.6f, 1f, 0.8f});

        addRow(table, header, "項次", "料號", "品名", "客戶料號", "數量", "單位");

        int itemNo = 1;
        for (DeliveryOrderLine line : lines) {
            CompanyItem item = items.get(line.getCompanyItemId());

            addRow(
                    table,
                    text,
                    String.valueOf(itemNo++),
                    item.getItemCode(),
                    item.getName(),
                    salesOrderLines.get(line.getSalesOrderLineId()).customerItemCode(),
                    line.getActualQuantity().stripTrailingZeros().toPlainString(),
                    line.getUnit().getDisplayName()
            );
        }

        return table;
    }

    //前四欄文字靠左，數量與單位靠右
    private void addRow(PdfPTable table, Font font, String... values) {
        for (int index = 0; index < values.length; index++) {
            int alignment = index < 4 ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT;
            table.addCell(pdfRenderer.cell(values[index], font, alignment));
        }
    }

    private static String deliveryMethodName(DeliveryMethod method) {
        return method == DeliveryMethod.CUSTOMER_PICKUP ? "客戶自取" : "外送";
    }

    private static String orDash(String value) {
        return value == null ? "－" : value;
    }
}
