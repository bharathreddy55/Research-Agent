package com.researchagent.cli;

import com.researchagent.agent.ResearchOrchestrator;
import com.researchagent.agent.RunEventListener;
import com.researchagent.model.AgentType;
import com.researchagent.model.ResearchDepth;
import com.researchagent.model.Run;
import com.researchagent.model.RunStatus;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

@Component
@Command(
        name = "research-agent",
        mixinStandardHelpOptions = true,
        version = "ResearchAgent 1.0.0",
        description = "Your AI Research Team, On Demand: Plans, Searches, Reads, Verifies, and Writes cited reports."
)
public class ResearchAgentCliRunner implements CommandLineRunner, Callable<Integer> {

    @Option(names = {"-q", "--query"}, description = "The research question to investigate", required = false)
    private String query;

    @Parameters(index = "0", arity = "0..1", description = "Positional research query (convenience)")
    private String positionalQuery;

    @Option(names = {"-d", "--depth"}, description = "Research depth: QUICK, STANDARD, DEEP", defaultValue = "STANDARD")
    private ResearchDepth depth = ResearchDepth.STANDARD;

    @Option(names = {"-b", "--budget"}, description = "Budget cap in paise (default: 1500 = ₹15)", defaultValue = "1500")
    private long budgetCapPaise = 1500;

    @Option(names = {"--server"}, description = "Run as background API server without executing CLI query")
    private boolean serverMode = false;

    private final ResearchOrchestrator orchestrator;

    public ResearchAgentCliRunner(ResearchOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public void run(String... args) throws Exception {
        if (args.length == 0) {
            // Default server mode or prompt
            System.out.println("===============================================================================");
            System.out.println(" 🔬 ResearchAgent: Multi-Agent AI Research System (Spring Boot + LangChain4j) ");
            System.out.println(" Run with --help for CLI options, or specify --query \"your question\"");
            System.out.println(" Web API is listening on http://localhost:8080");
            System.out.println("===============================================================================");
            return;
        }

        CommandLine cmd = new CommandLine(this);
        cmd.execute(args);
    }

    @Override
    public Integer call() {
        String activeQuery = (query != null && !query.isBlank()) ? query : positionalQuery;

        if (serverMode || activeQuery == null || activeQuery.isBlank()) {
            System.out.println("Starting ResearchAgent in Server Mode on port 8080...");
            return 0;
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println(" 🔬 RESEARCH AGENT: INITIATING MULTI-AGENT WORKFLOW");
        System.out.println(" Question: \"" + activeQuery + "\"");
        System.out.println(" Depth: " + depth + " | Budget Cap: " + budgetCapPaise + " paise (~₹" + (budgetCapPaise / 100.0) + ")");
        System.out.println("=".repeat(80) + "\n");

        RunEventListener consoleListener = (runId, status, agent, message, tokensIn, tokensOut, costPaise) -> {
            String prefix = switch (agent) {
                case PLANNER -> "📋 [PLANNER]             ";
                case SEARCHER -> "🔍 [SEARCHER]            ";
                case READER -> "📖 [READER]              ";
                case VERIFIER -> "🛡️ [VERIFIER]            ";
                case WRITER -> "✍️ [WRITER]              ";
                case REFLECTION_CRITIC -> "🤔 [REFLECTION CRITIC]   ";
                case CITATION_VALIDATOR -> "✅ [CITATION VALIDATOR]  ";
                default -> "⚙️ [SYSTEM]              ";
            };
            double inr = costPaise / 100.0;
            System.out.printf("%s %-50s (Tokens: %d in / %d out | Cost: ₹%.2f)%n",
                    prefix, message, tokensIn, tokensOut, inr);
        };

        Run run = orchestrator.executeRun(activeQuery, depth, budgetCapPaise, consoleListener);

        System.out.println("\n" + "=".repeat(80));
        if (run.getStatus() == RunStatus.COMPLETED && run.getReport() != null) {
            System.out.println(" 🎉 RESEARCH RUN COMPLETED SUCCESSFULLY!");
            System.out.printf(" Total Cost: ₹%.2f (%d paise) | Verified Claims: %d | Total Sources: %d%n",
                    run.getCostPaise() / 100.0, run.getCostPaise(),
                    run.getReport().getVerifiedClaimsCount(), run.getReport().getTotalSources());
            System.out.println(" Confidence Rating: " + String.format("%.1f%%", run.getReport().getConfidenceScore() * 100));
            System.out.println("=".repeat(80));

            System.out.println("\n### EXECUTIVE SUMMARY");
            for (String bullet : run.getReport().getExecutiveSummary()) {
                System.out.println("  • " + bullet);
            }

            System.out.println("\nFull Report Markdown preview (first 500 chars):");
            String md = run.getReport().getMarkdownContent();
            System.out.println(md.length() > 500 ? md.substring(0, 500) + "...\n[See ./reports/ for full file]" : md);
            return 0;
        } else {
            System.out.println(" ❌ RUN TERMINATED WITH STATUS: " + run.getStatus());
            if (run.getFailureReason() != null) {
                System.out.println(" Reason: " + run.getFailureReason());
            }
            return 1;
        }
    }
}
