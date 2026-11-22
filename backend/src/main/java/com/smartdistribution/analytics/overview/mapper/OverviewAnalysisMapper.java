package com.smartdistribution.analytics.overview.mapper;

import com.smartdistribution.analytics.cooling.dto.response.CoolingAnalysisResponse;
import com.smartdistribution.analytics.cooling.dto.response.CoolingAnalysisResponse.CustomerCooling;
import com.smartdistribution.analytics.cooling.service.CoolingLevel;
import com.smartdistribution.analytics.overview.dto.response.OverviewAnalysisResponse;
import com.smartdistribution.analytics.overview.dto.response.OverviewAnalysisResponse.BacklogCard;
import com.smartdistribution.analytics.overview.dto.response.OverviewAnalysisResponse.Cards;
import com.smartdistribution.analytics.overview.dto.response.OverviewAnalysisResponse.CoolingCard;
import com.smartdistribution.analytics.overview.dto.response.OverviewAnalysisResponse.CoolingCustomer;
import com.smartdistribution.analytics.overview.dto.response.OverviewAnalysisResponse.OverdueReceivableCard;
import com.smartdistribution.analytics.overview.dto.response.OverviewAnalysisResponse.PendingLists;
import com.smartdistribution.analytics.overview.dto.response.OverviewAnalysisResponse.ShortageImpactCard;
import com.smartdistribution.analytics.overview.dto.response.OverviewAnalysisResponse.ShortageItem;
import com.smartdistribution.analytics.overview.dto.response.OverviewAnalysisResponse.TopList;
import com.smartdistribution.analytics.receivable.dto.response.ReceivableAnalysisResponse;
import com.smartdistribution.analytics.receivable.dto.response.ReceivableAnalysisResponse.DueGroup;
import com.smartdistribution.analytics.sales.dto.response.SalesAnalysisResponse;
import com.smartdistribution.analytics.sales.dto.response.SalesAnalysisResponse.Backlog;
import com.smartdistribution.analytics.shortage.dto.response.ShortageAnalysisResponse;
import com.smartdistribution.analytics.shortage.dto.response.ShortageAnalysisResponse.ItemRisk;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * 從四個詳情分析的回應挑出總覽要的數字，不重新計算；清單沿用詳情的排序，只取前 10 筆
 */
@Component
public class OverviewAnalysisMapper {

    private static final int TOP_ITEMS = 10;

    /**
     * @return 營運總覽
     */
    public OverviewAnalysisResponse toResponse(
            SalesAnalysisResponse sales,
            ShortageAnalysisResponse shortages,
            CoolingAnalysisResponse cooling,
            ReceivableAnalysisResponse receivables
    ) {
        Backlog backlog = sales.backlog();
        DueGroup overdue = receivables.summary().overdue();
        //總覽只列要處理的：HIGH、MEDIUM；詳情已經依等級排好，等級是 null 的都在後面
        List<CustomerCooling> coolingCustomers = cooling.customers().stream()
                .filter(customer -> customer.level() != null)
                .toList();

        return new OverviewAnalysisResponse(
                sales.period(),
                receivables.asOf(),
                new Cards(
                        sales.comparison().revenue(),
                        sales.comparison().netOrderAmount(),
                        new BacklogCard(backlog.amount(), backlog.overdueAmount()),
                        new ShortageImpactCard(backlog.shortageImpactAmount(), backlog.shortageImpactItemCount()),
                        new CoolingCard(
                                coolingCustomers.size(),
                                (int) coolingCustomers.stream()
                                        .filter(customer -> customer.level() == CoolingLevel.HIGH)
                                        .count()
                        ),
                        new OverdueReceivableCard(
                                overdue.outstandingAmount(),
                                overdue.count(),
                                overdue.unconfirmedCount()
                        )
                ),
                sales.trend(),
                new PendingLists(
                        top(shortages.items(), OverviewAnalysisMapper::toShortageItem),
                        top(coolingCustomers, OverviewAnalysisMapper::toCoolingCustomer),
                        top(receivables.dueReceivables(), Function.identity())
                )
        );
    }

    //清單已經依詳情的規則排好，這裡只取前 10 筆並附上總筆數
    private static <S, T> TopList<T> top(List<S> all, Function<S, T> mapper) {
        return new TopList<>(all.size(), all.stream().limit(TOP_ITEMS).map(mapper).toList());
    }

    private static ShortageItem toShortageItem(ItemRisk item) {
        return new ShortageItem(
                item.companyItemId(),
                item.itemCode(),
                item.itemName(),
                item.stockRisks(),
                item.supplyRisks(),
                item.earliestDeliveryDate(),
                item.affectedLines().size()
        );
    }

    private static CoolingCustomer toCoolingCustomer(CustomerCooling customer) {
        return new CoolingCustomer(
                customer.customerId(),
                customer.customerCode(),
                customer.customerName(),
                customer.level(),
                customer.daysSinceLastTrade(),
                customer.averageCycleDays(),
                customer.averageMonthlyRevenue()
        );
    }
}
