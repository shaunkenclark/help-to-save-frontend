Feature: Applicant has missing mandatory information

  Scenario: User has a gg account but their surname is missing
    Given an user is in receipt of working tax credit
    When they apply for Help to Save with missing surname
    Then they see that their surname couldn't be retrieved

  Scenario: User has a gg account but the first line of their address is missing
    Given an user is in receipt of working tax credit
    When they apply for Help to Save with missing address line
    Then they see that the first line of their address couldn't be retrieved

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