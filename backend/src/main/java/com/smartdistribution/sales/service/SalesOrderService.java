package com.smartdistribution.sales.service;

import com.smartdistribution.sales.dto.request.CreateSalesOrderRequest;
import com.smartdistribution.sales.dto.request.UpdateSalesOrderRequest;
import com.smartdistribution.sales.dto.response.ResponsibleSalesOptionResponse;
import com.smartdistribution.sales.dto.response.SalesOrderConfirmationFile;
import com.smartdistribution.sales.dto.response.SalesOrderDetailResponse;
import com.smartdistribution.sales.dto.response.SalesOrderShortageResponse;
import com.smartdistribution.sales.dto.response.SalesOrderSummaryResponse;
import com.smartdistribution.sales.entity.SalesOrderStatus;
import com.smartdistribution.shared.web.request.VersionRequest;
import com.smartdistribution.shared.web.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SalesOrderService {

    PageResponse<SalesOrderSummaryResponse> findSalesOrders(
            String keyword,
            SalesOrderStatus status,
            Long customerId,
            Pageable pageable
    );

    SalesOrderDetailResponse findSalesOrder(Long salesOrderId);

    /**
     * 缺料清單：所有已核准 SO 中尚待規劃大於 0 的明細，依交期排序，不分頁
     *
     * @param companyItemId       料號篩選，null 代表不篩選
     * @param preferredSupplierId 偏好供應商篩選，null 代表不篩選
     */
    List<SalesOrderShortageResponse> findShortages(Long companyItemId, Long preferredSupplierId);

    /**
     * 負責業務的選項：和建立 SO 時檢查負責業務用同一條規則，選得到的人送出時一定會通過
     * 查 SO 的人也拿得到，讓清單與分析可以依負責業務篩選
     *
     * @return 啟用中、可以建立 SO 的使用者，依姓名排序
     */
    List<ResponsibleSalesOptionResponse> findResponsibleSalesOptions();

    SalesOrderDetailResponse createSalesOrder(CreateSalesOrderRequest request);

    SalesOrderDetailResponse updateSalesOrder(Long salesOrderId, UpdateSalesOrderRequest request);

    /**
     * 送審：檢查主檔、單位、價格與交期，保存交易快照後建立核准申請
     */
    SalesOrderDetailResponse submitSalesOrder(Long salesOrderId, VersionRequest request);

    /**
     * 訂單確認單 PDF：只有已核准、已結案的 SO 可以列印；隨時產生、不存檔
     * 版次 ＝ 1 ＋ 已核准的取消申請張數，同一版次重印內容相同
     */
    SalesOrderConfirmationFile printConfirmation(Long salesOrderId);

    /**
     * 放棄草稿：DRAFT / RETURNED → CANCELLED
     */
    SalesOrderDetailResponse cancelSalesOrder(Long salesOrderId, VersionRequest request);
}
