# csv-processing

A sample repo for doing basic data processing with out of a CSV with the Clojure Standard Library

## Usage

```
lein run
```

### Prompt
StartingData.csv consists of user ids, starting account balances, and what program the user is in. There are then 3 files with data about deposits and withdraws.

#### Program 1
* Can avoid paying a $8 penalty per month if one of the following criteria is met:
** deposited at least 300 per month
** has at least 5 transactions per month
** has at least 1200 in their account by the end of the month

#### Program 2
* Can avoid paying a $4 penalty per month if one of the following criteria is met:
** deposited at least 800 per month
** has at least 1 transaction per month
** has at least 5000 in their account by the end of the month

#### Please determine
1. which users have accrued penalties and how much each one owes
2. per month, which user deposited the most and how much it was
3. per month, which user had the most transactions and how many


## License

This program is free software. It comes without any warranty, to the extent permitted by applicable law. You can redistribute it and/or modify it under the terms of the Do What The Fuck You Want To Public License, Version 2, as published by Sam Hocevar. See http://www.wtfpl.net/ for more details.
