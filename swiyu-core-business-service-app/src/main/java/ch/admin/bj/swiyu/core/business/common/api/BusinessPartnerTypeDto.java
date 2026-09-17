package ch.admin.bj.swiyu.core.business.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "BusinessPartnerType",
    enumAsRef = true,
    description = """
    | Value                        | Description                                                   |
    | ---------------------------- | ------------------------------------------------------------- |
    | GOVERNMENTAL_INSTITUTION     | Governmental institutions like JPD or ASTRA                   |
    | BUSINESS                     | Business entities and corporations                            |
    | INDIVIDUAL                   | Individual persons                                            |
    | UNKNOWN                      | Deprecated – legacy placeholder, all entries migrated to BUSINESS |
    """
)
public enum BusinessPartnerTypeDto {
    GOVERNMENTAL_INSTITUTION,
    BUSINESS,
    INDIVIDUAL,
}
