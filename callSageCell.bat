python sagecell-client.py https://sagecell.sagemath.org "unix_start_of_int_A_Abs = var('unix_start_of_int_A_Abs'); unix_end_of_int_A_Abs = var('unix_end_of_int_A_Abs'); unix_start_of_int_3_Days = var('unix_start_of_int_3_Days'); unix_end_of_int_3_Days = var('unix_end_of_int_3_Days'); solve([unix_end_of_int_3_Days - unix_start_of_int_3_Days == 259200, unix_start_of_int_A_Abs < unix_end_of_int_A_Abs, unix_start_of_int_3_Days < unix_end_of_int_3_Days, unix_start_of_int_A_Abs == unix_start_of_int_3_Days, unix_end_of_int_A_Abs == unix_end_of_int_3_Days], [unix_start_of_int_3_Days, unix_end_of_int_3_Days])"