#!/usr/bin/env Rscript
# Usage: Rscript scripts/plot_benchmark.R <input.csv> <output.png>
# Expects columns: scenario, iteration, ms

args <- commandArgs(trailingOnly = TRUE)
if (length(args) < 2) {
  stop("Usage: Rscript scripts/plot_benchmark.R <input.csv> <output.png>")
}

input_path  <- args[[1]]
output_path <- args[[2]]

if (!file.exists(input_path)) {
  stop("Input file not found: ", input_path)
}

suppressPackageStartupMessages(library(ggplot2))

df <- read.csv(input_path, stringsAsFactors = FALSE)
required <- c("scenario", "iteration", "ms")
missing <- setdiff(required, names(df))
if (length(missing) > 0) {
  stop("CSV missing columns: ", paste(missing, collapse = ", "))
}

df$scenario <- factor(
    x      = df$scenario,
    levels = unique(df$scenario),
    labels = c("agckt", "process builder")
)

p <- ggplot(df, aes(x = .data[["scenario"]], y = .data[["ms"]])) +
  geom_boxplot() +
  labs(
    title = "Benchmark iteration times (ms per full pass)",
    x = "Scenario",
    y = "Time (ms)",
  )

ggsave(output_path, p, width = 4.5, height = 4.5, dpi = 300)
message("Figure written: ", normalizePath(output_path, mustWork = FALSE))
