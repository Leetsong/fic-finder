package simonlee.elegant;

import simonlee.elegant.core.configurations.Parser;
import simonlee.elegant.core.environment.Environment;
import simonlee.elegant.core.finder.Finder;
import simonlee.elegant.core.reporter.Reporter;
import simonlee.elegant.core.tracker.Tracker;
import simonlee.elegant.models.ApiContext;
import simonlee.elegant.utils.PubSub;
import com.sun.istack.internal.NotNull;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.manifest.ProcessManifest;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.toolkits.callgraph.CallGraph;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Container is a delegate following delegating pattern
// All component inside Container are unaware of each other
// what they know is only the Container.
public class Container {

    // soot-unaware container-unaware components
    private Parser   configParser;
    private Tracker  tracker;

    // soot-aware container-aware components
    private Reporter    reporter;
    private Finder      finder;
    private Environment environment;

    {
        configParser = new Parser();
        tracker      = new Tracker();

        reporter     = new Reporter(this);
        environment  = new Environment(this);
        finder       = new Finder(this);
    }

    public void run(@NotNull List<String> configs) {
        configParser.parse(configs);
        finder.run();
        reporter.report(environment.getOutput());
        environment.getOutput().close();
    }

    // delegate Environment

    public String[] getOptions() {
        return environment.getOptions();
    }

    public SetupApplication getApp() {
        return environment.getApp();
    }

    public ProcessManifest getManifest() {
        return environment.getManifest();
    }

    public PrintStream getOutput() {
        return environment.getOutput();
    }

    public Set<ApiContext> getModels() {
        return environment.getModels();
    }

    public boolean isVerbose() {
        return environment.isVerbose();
    }

    public IInfoflowCFG getInterproceduralCFG() {
        return environment.getInterproceduralCFG();
    }

    public CallGraph getCallGraph() {
        return environment.getCallGraph();
    }

    public String getAppName() {
        return environment.getAppName();
    }

    public String getAppPackage() {
        return environment.getAppPackage();
    }

    // delegate ConfigParser, parser is a publisher, so delegate it

    public Map<String, String> getArgs() {
        return configParser.getArgs();
    }

    public String getArg(String key) {
        return configParser.getArg(key);
    }

    public int watchArgs(PubSub.Handle handle) {
        return configParser.subscribe(handle);
    }

    public void unwatchArgs(int handler) {
        configParser.unsubscribe(handler);
    }

    // delegate Tracker, tracker is a publisher, so delegate it

    public void emitIssue(PubSub.Message message) {
        this.tracker.publish(message);
    }

    public int watchIssues(PubSub.Handle handle) {
        return tracker.subscribe(handle);
    }

    public void unwatchIssues(int handler) {
        tracker.unsubscribe(handler);
    }

    // delegate Reporter

    public void submit(ApiContext model, Reporter.Information info) {
        reporter.submit(model, info);
    }
}
