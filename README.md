[![Symphony Software Foundation - Archived](https://cdn.rawgit.com/symphonyoss/contrib-toolbox/master/images/ssf-badge-archived.svg)](https://symphonyoss.atlassian.net/wiki/display/FM/Project+lifecycle)

# Archived Project
This project was archived 2016-11-07.  For more information on how this may affect consumers and contributors of this project, please see the Foundation's [Project Lifecycle documentation](https://symphonyoss.atlassian.net/wiki/display/FM/Project+lifecycle).


=================================README===========================

LunchBoxBot is a useful Symphony Bot that consumes lunch menu posted everyday and helps you make lunch experience better

It has following functions:

1. Menu: Viewing today's menu. Command: /lunchbox menu OR /lunchbox today's menu
2. Tomorrow's menu: Viewing tomorrow's menu. This could be extended to a week as well with some tweaking. Command: /lunchbox tomorrow
3. Feedback: Giving feedback for today's food. It is based on today's menu and user can submit feedback for individual items/lunch as a whole. Command: /lunchbox feedback
4. Format: Checking feedback command format. "- For feedback on individual items on the menu, <item number> -> <number of stars (out of 5)>\n- For feedback on lunch as a whole, <overall> -> <number of stars (out of 5)>, comments (optional)\n\n\nP.S: Please provide complete feedback in a single message with each section separated by a comma". Command: /lunchbox format
5. Help: Displays all available options. Command: /lunchbox help

User's feedback is stored locally as of now in an xls file. 
Note that user can change their feedback as long as it is for the same day.

To run this bot:
* Have Symphony OSS files ready to be run.
* checkout source code from the Git Repo.
* This bot works only on corporate right now and in that specific room only. This threadId is configured in the target settings.

Stay hungry. Stay foolish

==================================================================
Bon Voyage - Shwetha Gopalan, Aug 9 2016
