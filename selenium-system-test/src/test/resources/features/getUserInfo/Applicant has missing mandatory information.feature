Feature: Applicant has missing mandatory information

 Scenario Outline: Mandatory field is missing
    Given an user is in receipt of working tax credit
    When an applicant applies for Help to Save and their "<field>" is missing from DES
    Then they see that their "<field>" is missing

  Examples:
  |field|
  |Forename|
  |Surname|
  |date of birth|
  |address1|
  |address2|
  |postcode|
  |country code|
  #|email|