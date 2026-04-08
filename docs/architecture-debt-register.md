# PC-AML Architecture Debt Register

## Format

| debt_id | module | source_path | layer_tag | current_state | future_target | priority | replacement_action | blocking_condition |
|---|---|---|---|---|---|---|---|---|

---

## Debt Items

### AML-D001: Salv AML Integration Not Migrated

| Field | Value |
|---|---|
| debt_id | AML-D001 |
| module | pc-aml / sanctions |
| source_path | `com.gd.channel.salvaml.SalvAmlService` (460 lines), `SalvAmlInfService` (508 lines) |
| layer_tag | PC-aml |
| current_state | Old system still uses Salv AML for customer/transaction monitoring. Not yet migrated to pc-aml. |
| future_target | Add `SalvAmlProvider` implementing `ScreeningProvider` interface in pc-aml |
| priority | P1 |
| replacement_action | Implement SalvAmlProvider with OAuth token management, customer/trade screening |
| blocking_condition | Need Salv AML API credentials provisioned for new service |

### AML-D002: DowJones Real-Time API Not Migrated

| Field | Value |
|---|---|
| debt_id | AML-D002 |
| module | pc-aml / sanctions |
| source_path | `com.gd.channel.dowjones.DowjonesInfService` (61 lines) |
| layer_tag | PC-aml |
| current_state | Old system uses DowjonesInfService for real-time queries. pc-aml currently uses PFA file-based import only. |
| future_target | Add real-time DowJones API query capability alongside file-based import |
| priority | P1 |
| replacement_action | Implement DJ API client in `DowJonesProvider` for real-time screening |
| blocking_condition | DJ API endpoint and credentials must be provisioned |

### AML-D003: DowJones Whitelist Management Partially Migrated

| Field | Value |
|---|---|
| debt_id | AML-D003 |
| module | pc-aml / blacklist |
| source_path | `com.gd.pe.fk.service.DowJonesWhiteListService` (202 lines) |
| layer_tag | PC-aml |
| current_state | Old whitelist exemption logic (monitorWhiteList, handleWhiteList) not yet in pc-aml. Note: whitelist disabled as of 2025-05-03 per code comment. |
| future_target | If whitelist re-enabled, implement as exemption rule in BlacklistCheckService |
| priority | P2 |
| replacement_action | Add whitelist exemption table + service if business requires |
| blocking_condition | Business decision on whether whitelist exemptions are reinstated |

### AML-D004: TradeCheckService.checkUserProof (Step 1)

| Field | Value |
|---|---|
| debt_id | AML-D004 |
| module | pc-aml / evidence |
| source_path | `TradeCheckService.checkUserProof` (line 1479) |
| layer_tag | PC-kyc (NOT PC-aml) |
| current_state | Checks if user has business proof documents. This is KYC evidence, not AML. |
| future_target | Move to `pc-kyc` — document completeness check |
| priority | P2 |
| replacement_action | pc-kyc exposes document status API; L3 checks via BankKycGateway.queryKycStatus |
| blocking_condition | pc-kyc must be built |

### AML-D005: TradeCheckService.checkEddRule (Steps 60-62)

| Field | Value |
|---|---|
| debt_id | AML-D005 |
| module | pc-aml boundary |
| source_path | `TradeCheckService.checkEddRule` (line 3042), `checkEddRuleTransferOut` (line 3165) |
| layer_tag | PC-risk (NOT PC-aml) |
| current_state | EDD rules enforce transaction amount limits (single/day/month/year). This is risk/limit policy, not AML screening. |
| future_target | Move to `pc-risk` — transaction limit policy engine |
| priority | P1 |
| replacement_action | pc-risk provides EDD limit evaluation; L3 calls via BankKycGateway.evaluateRisk |
| blocking_condition | pc-risk must be built |

### AML-D006: TradeCheckService.checkCertificateExpired (Step 61)

| Field | Value |
|---|---|
| debt_id | AML-D006 |
| module | pc-aml boundary |
| source_path | `TradeCheckService.checkCertificateExpired` (line 3289) |
| layer_tag | PC-kyc (NOT PC-aml) |
| current_state | Checks if ID document expired > 60 days. This is KYC identity validation, not AML. |
| future_target | Move to `pc-kyc` — document expiry check |
| priority | P2 |
| replacement_action | pc-kyc exposes identity validity check; L3 calls via BankKycGateway.queryKycStatus |
| blocking_condition | pc-kyc must be built |

### AML-D007: TradeCheckService.checkAccountName (Step 56)

| Field | Value |
|---|---|
| debt_id | AML-D007 |
| module | pc-aml boundary |
| source_path | `TradeCheckService.checkAccountName` (line 2711) |
| layer_tag | PC-risk (NOT PC-aml) |
| current_state | SEPA payer/payee name matching. This is fraud detection, not AML screening. |
| future_target | Move to `pc-risk` — beneficiary name verification |
| priority | P2 |
| replacement_action | pc-risk provides name verification service |
| blocking_condition | pc-risk must be built |

### AML-D008: RiskCountryService Audit Workflow

| Field | Value |
|---|---|
| debt_id | AML-D008 |
| module | pc-aml / restriction |
| source_path | `RiskCountryService.auditRequest` (line ~300), `createRiskCountry` |
| layer_tag | PC-aml |
| current_state | Old system has maker-checker workflow for adding/removing risk countries. pc-aml has the data model but not the audit workflow. |
| future_target | Add maker-checker approval flow for risk country modifications |
| priority | P1 |
| replacement_action | Implement audit trail + approval endpoints for risk country CRUD |
| blocking_condition | None |

### AML-D009: Blacklist Data Migration

| Field | Value |
|---|---|
| debt_id | AML-D009 |
| module | pc-aml / blacklist |
| source_path | Old system `UserBlackList`, `ExpressBlackRecord`, `TransactionBlackList` tables |
| layer_tag | PC-aml |
| current_state | Existing blacklist data in old DB must be migrated to pc-aml `blacklist_entry` table |
| future_target | One-time data migration script from old tables to new schema |
| priority | P0 |
| replacement_action | Write Flyway migration or ETL script to import existing blacklist data |
| blocking_condition | Database access to old system tables |

### AML-D010: Risk Country Data Migration

| Field | Value |
|---|---|
| debt_id | AML-D010 |
| module | pc-aml / restriction |
| source_path | Old system `risk_country`, `dict("risk_template")` tables |
| layer_tag | PC-aml |
| current_state | Existing risk country config in old DB (dictService.getText "risk_template") must be migrated |
| future_target | One-time data migration to `risk_country` table |
| priority | P0 |
| replacement_action | Extract country lists from old dict tables, insert into pc-aml |
| blocking_condition | Database access to old system tables |

### AML-D011: Org Restriction Data Migration

| Field | Value |
|---|---|
| debt_id | AML-D011 |
| module | pc-aml / restriction |
| source_path | Old system `OrgRegistrationRestriction` table |
| layer_tag | PC-aml |
| current_state | Existing org restriction config must be migrated to pc-aml `org_restriction` table |
| future_target | One-time data migration |
| priority | P1 |
| replacement_action | Extract from old DB, insert into pc-aml |
| blocking_condition | Database access to old system tables |

### AML-D012: Transaction Monitoring (Salv) Event-Driven Integration

| Field | Value |
|---|---|
| debt_id | AML-D012 |
| module | pc-aml / sanctions |
| source_path | `SalvAmlService.taskSendSalvtask1-4`, `SalvAmlNotificationService` |
| layer_tag | PC-aml |
| current_state | Old system has scheduled tasks pushing customer/trade data to Salv and receiving webhook alerts. Not yet replicated. |
| future_target | Implement Salv webhook handler and scheduled sync in pc-aml |
| priority | P2 |
| replacement_action | Add SalvAmlProvider + webhook controller + scheduled push |
| blocking_condition | Salv API credentials and webhook endpoint configuration |

---

## Summary

| Priority | Count | Items |
|---|---|---|
| P0 | 2 | AML-D009, AML-D010 (data migration) |
| P1 | 4 | AML-D001, AML-D002, AML-D005, AML-D008 |
| P2 | 6 | AML-D003, AML-D004, AML-D006, AML-D007, AML-D011, AML-D012 |

## Boundary Clarification

Items AML-D004, AML-D005, AML-D006, AML-D007 are explicitly **NOT pc-aml** responsibilities:

- AML-D004 (checkUserProof) → **pc-kyc**
- AML-D005 (checkEddRule) → **pc-risk**
- AML-D006 (checkCertificateExpired) → **pc-kyc**
- AML-D007 (checkAccountName) → **pc-risk**

These are listed here to ensure complete traceability from old `TradeCheckService` to new target layers.
