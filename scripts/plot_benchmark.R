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

df$scenario <- factor(df$scenario, levels = unique(df$scenario))

scenarios <- levels(df$scenario)
summ <- do.call(rbind, lapply(scenarios, function(s) {
    x <- df$ms[df$scenario == s]
    n <- length(x)
    se <- if (n > 1) stats::sd(x) / sqrt(n) else 0
    data.frame(scenario = s, mean_ms = mean(x), se = se, stringsAsFactors = FALSE)
}))
summ$scenario <- factor(summ$scenario, levels = scenarios)

p <- summ |>
    ggplot() +
    aes(
        x = .data[["scenario"]],
        y = .data[["mean_ms"]],
        fill = .data[["scenario"]]
    ) +
    geom_col(width = 0.50) +
    geom_errorbar(
        aes(
            ymin = pmax(0, .data[["mean_ms"]] - .data[["se"]]),
            ymax = .data[["mean_ms"]] + .data[["se"]],
        ),
        width = 0.2,
        linewidth = 0.35,
    ) +
    scale_y_continuous(
        limits = c(0, NA),
        expand = expansion(mult = c(0, 0.06)),
    ) +
    labs(
        title = "Benchmark iteration times (ms per full pass)",
        subtitle = "(lower is better)",
        x = "Scenario",
        y = "Time (ms)",
    ) +
    theme(legend.position = "none")

ggsave(output_path, p, width = 5, height = 5.5, dpi = 300)
message("Figure written: ", normalizePath(output_path, mustWork = FALSE))
