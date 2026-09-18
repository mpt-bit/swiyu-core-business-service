package ch.admin.bj.swiyu.core.business.common.service.mapper;

import ch.admin.bj.swiyu.core.business.common.api.BusinessPartnerTypeDto;
import ch.admin.bj.swiyu.core.business.common.domain.BusinessPartnerType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BusinessPartnerTypeMapper {

    public static BusinessPartnerType toBusinessPartnerType(BusinessPartnerTypeDto businessPartnerTypeDto) {
        if (businessPartnerTypeDto == null) {
            return BusinessPartnerType.BUSINESS;
        }

        return switch (businessPartnerTypeDto) {
            case GOVERNMENTAL_INSTITUTION -> BusinessPartnerType.GOVERNMENTAL_INSTITUTION;
            case BUSINESS -> BusinessPartnerType.BUSINESS;
            case INDIVIDUAL -> BusinessPartnerType.INDIVIDUAL;
        };
    }

    public static BusinessPartnerTypeDto toBusinessPartnerTypeDto(BusinessPartnerType businessPartnerType) {
        return switch (businessPartnerType) {
            case GOVERNMENTAL_INSTITUTION -> BusinessPartnerTypeDto.GOVERNMENTAL_INSTITUTION;
            case BUSINESS -> BusinessPartnerTypeDto.BUSINESS;
            case INDIVIDUAL -> BusinessPartnerTypeDto.INDIVIDUAL;
        };
    }
}
