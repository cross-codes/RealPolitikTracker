import pandas as pd

df = pd.read_excel("input.xlsx", sheet_name="Sheet1")
df.to_csv("list.csv", index=False)
