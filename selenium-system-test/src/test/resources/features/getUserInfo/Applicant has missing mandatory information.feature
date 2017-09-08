Feature: Applicant has missing mandatory information

  Scenario: User has a gg account but their surname is missing
    #Given a user has logged in with a confidence level of 200
    Given an user is in receipt of working tax credit
    When they apply for Help to Save with missing surname
    Then they see that their surname couldn't be retrieved

  Scenario: User has a gg account but the first line of their address is missing
    Given an user is NOT in receipt of working tax credit
    When they apply for Help to Save with missing address line
    Then they see that the first line of their address couldn't be retrieved