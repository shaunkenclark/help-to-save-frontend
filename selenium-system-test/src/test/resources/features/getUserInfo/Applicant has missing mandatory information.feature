Feature: Applicant has missing mandatory information

  Scenario: Applicant has a gg account but their surname is missing
    Given an applicant is in receipt of working tax credit
    When they apply for Help to Save
    Then they see that their surname couldn't be retrieved

  Scenario: Applicant has a gg account but the first line of their address is missing
    Given an applicant is NOT in receipt of working tax credit
    When they apply for Help to Save
    Then they see that the first line of their address couldn't retrieved