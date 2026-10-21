package com.smartdistribution.masterdata.companyitem.service.impl;

import com.smartdistribution.approval.entity.ApprovalTargetType;
import com.smartdistribution.approval.service.ApprovalService;
import com.smartdistribution.audit.entity.AuditActionType;
import com.smartdistribution.audit.service.AuditService;
import com.smartdistribution.inventory.dto.response.InventoryPolicyResponse;
import com.smartdistribution.inventory.service.InventoryService;
import com.smartdistribution.masterdata.common.MasterDataStatus;
import com.smartdistribution.masterdata.common.UnitOfMeasure;
import com.smartdistribution.masterdata.companyitem.dto.request.ChangeCompanyItemStatusRequest;
import com.smartdistribution.masterdata.companyitem.dto.request.CreateCompanyItemRequest;
import com.smartdistribution.masterdata.companyitem.dto.request.ReplaceUnitConversionsRequest;
import com.smartdistribution.masterdata.companyitem.dto.request.SubmitCompanyItemRequest;
import com.smartdistribution.masterdata.companyitem.dto.request.UnitConversionRequest;
import com.smartdistribution.masterdata.companyitem.dto.request.UpdateCompanyItemRequest;
import com.smartdistribution.masterdata.companyitem.dto.response.CompanyItemDetailResponse;
import com.smartdistribution.masterdata.companyitem.dto.response.CompanyItemSummaryResponse;
import com.smartdistribution.masterdata.companyitem.entity.CompanyItem;
import com.smartdistribution.masterdata.companyitem.entity.ItemUnitConversion;
import com.smartdistribution.masterdata.companyitem.mapper.CompanyItemMapper;
import com.smartdistribution.masterdata.companyitem.repository.CompanyItemRepository;
import com.smartdistribution.masterdata.companyitem.repository.ItemUnitConversionRepository;
import com.smartdistribution.masterdata.companyitem.service.CompanyItemService;
import com.smartdistribution.masterdata.supplier.entity.Supplier;
import com.smartdistribution.masterdata.supplier.repository.SupplierRepository;
import com.smartdistribution.shared.exception.BusinessException;
import com.smartdistribution.shared.exception.ErrorCode;
import com.smartdistribution.shared.security.currentuser.CurrentUserProvider;
import com.smartdistribution.shared.web.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyItemServiceImpl implements CompanyItemService {

    private final CompanyItemRepository companyItemRepository;
    private final ItemUnitConversionRepository itemUnitConversionRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryService inventoryService;
    private final ApprovalService approvalService;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;
    private final CompanyItemMapper companyItemMapper;

    @Override
    @PreAuthorize("hasAuthority('COMPANY_ITEM_VIEW')")
    public PageResponse<CompanyItemSummaryResponse> findCompanyItems(
            String keyword,
            MasterDataStatus status,
            Pageable pageable
    ) {
        String normalizedKeyword =
                keyword == null || keyword.isBlank() ? null : keyword.strip();

        Page<CompanyItemSummaryResponse> items = companyItemRepository
                .searchByKeywordAndStatus(normalizedKeyword, status, pageable)
                .map(companyItemMapper::toSummary);

        return PageResponse.from(items);
    }

    @Override
    @PreAuthorize("hasAuthority('COMPANY_ITEM_VIEW')")
    public CompanyItemDetailResponse findCompanyItem(Long companyItemId) {
        return toDetail(findCompanyItemById(companyItemId));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('COMPANY_ITEM_CREATE')")
    public CompanyItemDetailResponse createCompanyItem(CreateCompanyItemRequest request) {
        String itemCode = CompanyItem.normalizeCode(request.itemCode());

        if (companyItemRepository.existsByItemCode(itemCode)) {
            throw new BusinessException(ErrorCode.COMPANY_ITEM_CODE_ALREADY_EXISTS);
        }

        ensurePreferredSupplierIsActive(request.preferredSupplierId());

        CompanyItem item = new CompanyItem(
                request.itemCode(),
                request.name(),
                request.baseUnit(),
                request.preferredSupplierId(),
                currentUserProvider.getCurrentUserId()
        );
        companyItemRepository.saveAndFlush(item);

        //料號建立時同步建立安全庫存政策與庫存餘額列
        inventoryService.initializeForCompanyItem(item.getId());

        return toDetail(item);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('COMPANY_ITEM_UPDATE')")
    public CompanyItemDetailResponse updateCompanyItem(
            Long companyItemId,
            UpdateCompanyItemRequest request
    ) {
        CompanyItem item = findCompanyItemById(companyItemId);
        verifyVersion(item, request.version());

        boolean preferredSupplierChanged =
                !Objects.equals(request.preferredSupplierId(), item.getPreferredSupplierId());

        if (item.isDraftOrReturned()) {
            String itemCode = CompanyItem.normalizeCode(request.itemCode());

            if (!itemCode.equals(item.getItemCode())
                    && companyItemRepository.existsByItemCode(itemCode)) {
                throw new BusinessException(ErrorCode.COMPANY_ITEM_CODE_ALREADY_EXISTS);
            }

            //既有倍率都以舊的基準單位計算，已有換算時不能直接改基準單位
            if (request.baseUnit() != item.getBaseUnit()) {
                if (itemUnitConversionRepository.existsByCompanyItemId(companyItemId)) {
                    throw new BusinessException(ErrorCode.COMPANY_ITEM_UNIT_CONVERSIONS_EXIST);
                }
            }

            if (preferredSupplierChanged) {
                ensurePreferredSupplierIsActive(request.preferredSupplierId());
            }

            item.updateDraft(
                    request.itemCode(),
                    request.name(),
                    request.baseUnit(),
                    request.preferredSupplierId()
            );
            companyItemRepository.flush();

            return toDetail(item);
        }

        if (item.getStatus() != MasterDataStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.MASTER_DATA_STATUS_CONFLICT);
        }

        //已啟用時，料號、品名與基準單位鎖定，只能改偏好供應商
        if (item.isIdentityChanged(request.itemCode(), request.name(), request.baseUnit())) {
            throw new BusinessException(ErrorCode.MASTER_DATA_IDENTITY_LOCKED);
        }

        if (preferredSupplierChanged) {
            ensurePreferredSupplierIsActive(request.preferredSupplierId());
        }

        Long beforeSupplierId = item.getPreferredSupplierId();
        item.changePreferredSupplier(request.preferredSupplierId());
        companyItemRepository.flush();

        auditService.record(
                currentUserProvider.getCurrentUserId(),
                AuditActionType.COMPANY_ITEM_UPDATE,
                companyItemId,
                "修改公司料號偏好供應商",
                "preferredSupplierId=" + beforeSupplierId,
                "preferredSupplierId=" + item.getPreferredSupplierId(),
                null
        );

        return toDetail(item);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('COMPANY_ITEM_UPDATE')")
    public CompanyItemDetailResponse replaceUnitConversions(
            Long companyItemId,
            ReplaceUnitConversionsRequest request
    ) {
        CompanyItem item = findCompanyItemById(companyItemId);
        verifyVersion(item, request.version());

        //不能包含基準單位，也不能重複
        Set<UnitOfMeasure> units = EnumSet.noneOf(UnitOfMeasure.class);
        for (UnitConversionRequest conversion : request.unitConversions()) {
            if (conversion.unit() == item.getBaseUnit() || !units.add(conversion.unit())) {
                throw new BusinessException(ErrorCode.COMPANY_ITEM_UNIT_CONVERSION_INVALID);
            }
        }

        //讓料號版本加 1，同時檢查狀態，待核准與停用期間不能改
        item.markUnitConversionsChanged(Instant.now());

        List<ItemUnitConversion> existing =
                itemUnitConversionRepository.findAllByCompanyItemIdOrderByUnitAsc(companyItemId);

        String beforeData = conversionSnapshot(existing);

        itemUnitConversionRepository.deleteAll(existing);
        itemUnitConversionRepository.flush();

        List<ItemUnitConversion> replaced = new ArrayList<>();

        for (UnitConversionRequest conversion : request.unitConversions()) {
            replaced.add(
                    new ItemUnitConversion(
                            item,
                            conversion.unit(),
                            conversion.factor()
                    )
            );
        }
        itemUnitConversionRepository.saveAll(replaced);
        companyItemRepository.flush();

        //草稿階段的修改尚未生效，不寫稽核
        if (item.getStatus() == MasterDataStatus.ACTIVE) {
            auditService.record(
                    currentUserProvider.getCurrentUserId(),
                    AuditActionType.COMPANY_ITEM_UNIT_CONVERSIONS_REPLACE,
                    companyItemId,
                    "修改公司料號單位換算",
                    beforeData,
                    conversionSnapshot(replaced),
                    null
            );
        }

        return toDetail(item);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('COMPANY_ITEM_SUBMIT')")
    public CompanyItemDetailResponse submitCompanyItem(
            Long companyItemId,
            SubmitCompanyItemRequest request
    ) {
        CompanyItem item = findCompanyItemById(companyItemId);
        verifyVersion(item, request.version());

        item.submit();
        approvalService.submit(
                ApprovalTargetType.COMPANY_ITEM,
                companyItemId,
                currentUserProvider.getCurrentUserId()
        );
        companyItemRepository.flush();

        return toDetail(item);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('COMPANY_ITEM_STATUS_MANAGE')")
    public CompanyItemDetailResponse deactivateCompanyItem(
            Long companyItemId,
            ChangeCompanyItemStatusRequest request
    ) {
        CompanyItem item = findCompanyItemById(companyItemId);
        verifyVersion(item, request.version());

        item.deactivate();
        companyItemRepository.flush();

        recordStatusChange(
                item,
                AuditActionType.COMPANY_ITEM_DEACTIVATE,
                "停用公司料號",
                MasterDataStatus.ACTIVE,
                request.reason()
        );

        return toDetail(item);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('COMPANY_ITEM_STATUS_MANAGE')")
    public CompanyItemDetailResponse activateCompanyItem(
            Long companyItemId,
            ChangeCompanyItemStatusRequest request
    ) {
        CompanyItem item = findCompanyItemById(companyItemId);
        verifyVersion(item, request.version());

        item.activate();
        companyItemRepository.flush();

        recordStatusChange(
                item,
                AuditActionType.COMPANY_ITEM_ACTIVATE,
                "重新啟用公司料號",
                MasterDataStatus.INACTIVE,
                request.reason()
        );

        return toDetail(item);
    }

    private CompanyItemDetailResponse toDetail(CompanyItem item) {
        Supplier preferredSupplier = null;
        if (item.getPreferredSupplierId() != null) {
            preferredSupplier = supplierRepository.findById(item.getPreferredSupplierId()).orElse(null);
        }

        List<ItemUnitConversion> conversions =
                itemUnitConversionRepository.findAllByCompanyItemIdOrderByUnitAsc(item.getId());
        InventoryPolicyResponse inventoryPolicy = inventoryService.findInventoryPolicy(item.getId());

        return companyItemMapper.toDetail(item, preferredSupplier, conversions, inventoryPolicy);
    }

    private CompanyItem findCompanyItemById(Long companyItemId) {
        return companyItemRepository.findById(companyItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANY_ITEM_NOT_FOUND));
    }

    private void verifyVersion(CompanyItem item, Long expectedVersion) {
        if (!Objects.equals(item.getVersion(), expectedVersion)) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
    }

    //偏好供應商可為空；有設定時必須存在且已啟用
    private void ensurePreferredSupplierIsActive(Long supplierId) {
        if (supplierId == null) {
            return;
        }

        Supplier supplier = supplierRepository.findById(supplierId).orElse(null);

        if (supplier == null || supplier.getStatus() != MasterDataStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.COMPANY_ITEM_PREFERRED_SUPPLIER_INVALID);
        }
    }

    private void recordStatusChange(
            CompanyItem item,
            AuditActionType actionType,
            String summary,
            MasterDataStatus beforeStatus,
            String reason
    ) {
        auditService.record(
                currentUserProvider.getCurrentUserId(),
                actionType,
                item.getId(),
                summary,
                "status=" + beforeStatus,
                "status=" + item.getStatus(),
                reason.strip()
        );
    }

    private String conversionSnapshot(List<ItemUnitConversion> conversions) {
        List<String> values = new ArrayList<>();
        for (ItemUnitConversion conversion : conversions) {
            values.add(conversion.getUnit() + "=" + conversion.getFactor().toPlainString());
        }

        return String.join(", ", values);
    }
}
