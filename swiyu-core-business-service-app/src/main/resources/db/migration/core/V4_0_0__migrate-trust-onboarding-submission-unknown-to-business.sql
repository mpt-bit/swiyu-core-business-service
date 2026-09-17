-- Migrate legacy trust onboarding submission with UNKNOWN or INDIVIDUAL type to BUSINESS.
-- UNKNOWN was a temporary placeholder for partners onboarded before the type concept was introduced.
-- INDIVIDUAL was used by the old v1 onboarding flow; the new v2 flow always requires an explicit type,
-- and individual persons are not supported as business partners going forward.
UPDATE trust_onboarding_submission
SET requested_partner_type = 'BUSINESS'
WHERE requested_partner_type IN ('UNKNOWN', 'INDIVIDUAL') OR requested_partner_type IS NULL;