package simonlee.elegant.utils;

import simonlee.elegant.core.finder.CallSites;
import com.sun.istack.internal.NotNull;
import soot.*;
import soot.jimple.*;
import soot.jimple.infoflow.solver.cfg.IInfoflowCFG;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.*;
import soot.toolkits.graph.pdg.HashMutablePDG;
import soot.toolkits.graph.pdg.IRegion;
import soot.toolkits.graph.pdg.PDGNode;
import soot.toolkits.graph.pdg.ProgramDependenceGraph;
import soot.util.Chain;

import java.util.*;

public class Soots {

    private static Logger logger = new Logger(Soots.class);

    private static final String CLASS_STATIC_CODE_BLOCK_METHOD_NAME = "<clinit>";
    private static final String CLASS_CODE_BLOCK_METHOD_NAME        = "<init>";

    // invokingStmtsCache, as a cache, stores all statements that will invoke a method
    // the key is the method where the statement is
    private static Map<SootMethod, Set<Unit>> invokingStmtsCache = new HashMap<>(256);

    /**
     * findLatestDefinition will find the latest definition unit of value v at unit u in method m
     *
     * @param v the variable who wants to find its latest definition
     * @param u the unit where the variable lives at
     * @param m the method where the variable lives at
     * @return  the latest definition unit of v
     */
    public static Unit findLatestDefinition(@NotNull Value v, @NotNull Unit u, @NotNull SootMethod m) {
        if (!(u instanceof Stmt)) { return null; }

        Unit       def   = null;
        List<Unit> units = new ArrayList<>(m.getActiveBody().getUnits());
        int        index = units.indexOf(u);

        if (index != -1) {
            for (int i = index - 1; i >= 0; i --) {
                Stmt s = (Stmt) units.get(i);
                if (s instanceof IdentityStmt || s instanceof AssignStmt) {
                    try {
                        if (v.equals(s.getDefBoxes().get(0).getValue())) {
                            def = s;
                            break;
                        }
                    } catch (NullPointerException e) {
                        // do nothing
                    }
                }
            }
        }

        return def;
    }

    /**
     * findPreviousDefinitions will find all previous definitions unit of value v at unit u in method m
     *
     * @param v the variable who wants to find its previous definitions
     * @param u the unit where the variable lives at
     * @param m the method where the variable lives at
     * @return  the previous definitions unit of v
     */
    public static Set<Unit> findPreviousDefinitions(@NotNull Value v, @NotNull Unit u, @NotNull SootMethod m) {
        if (!(u instanceof Stmt)) { return new HashSet<>(); }

        try {
            Set<Unit>  defs  = new HashSet<>();
            List<Unit> units = new ArrayList<>(m.getActiveBody().getUnits());
            int        index = units.indexOf(u);

            if (index != -1) {
                for (int i = index - 1; i >= 0; i --) {
                    Stmt s = (Stmt) units.get(i);
                    if (s instanceof IdentityStmt || s instanceof AssignStmt) {
                        try {
                            if (v.equals(s.getDefBoxes().get(0).getValue())) {
                                defs.add(s);
                            }
                        } catch (NullPointerException e) {
                            // do nothing
                        }
                    }
                }
            }

            return defs;
        } catch (Exception e) {
            return new HashSet<>();
        }
    }

    /**
     * findNodeOf finds node of unit u in pdg
     *
     * @param u   the unit who wants to find its PDG node
     * @param pdg the pdg where the unit lives
     * @return    the PDG node where the unit lives at
     */
    public static PDGNode findNodeOf(Unit u, ProgramDependenceGraph pdg) {
        PDGNode node = null;

        for (PDGNode n : pdg) {
            Iterator<Unit> iterator = unitIteratorOfPDGNode(n);
            if (iterator == null) continue;

            while (iterator.hasNext()) {
                if (iterator.next().equals(u)) { node = n; break; }
            }

            if (node != null) { break; }
        }

        return node;
    }

    /**
     * findInternalForwardSlicing finds internal forward slicing units of unit u in pdg
     *
     * @param u   the unit who wants to find its internal forward slicing
     * @param pdg the pdg where the unit lives at
     * @return    the set of forward slicing in the pdg of the unit
     */
    public static Set<Unit> findInternalForwardSlicing(Unit u, ProgramDependenceGraph pdg) {
        Set<Unit> deps = new HashSet<>(128);

        // 1. find the corresponding PDGNode srcNode, which contains the unit
        PDGNode srcNode = findNodeOf(u, pdg);
        if (srcNode == null) { return deps; }

        // 2. find all the dependent PDGNodes of srcNode
        List<PDGNode> depOfCallerUnit = pdg.getDependents(srcNode);

        // 3. get all the units of each dependent PDGNode
        for (PDGNode dependent : depOfCallerUnit) {
            Iterator<Unit> iter = unitIteratorOfPDGNode(dependent);
            while (iter.hasNext()) {
                deps.add(iter.next());
            }
        }

        return deps;
    }

    /**
     * findInternalBackwardSlicing finds internal backward slicing units of unit u in pdg
     *
     * @param u   the unit who wants to find its internal backward slicing
     * @param pdg the pdg where the unit lives at
     * @return    the set of backward slicing in the pdg of the unit
     */
    public static Set<Unit> findInternalBackwardSlicing(Unit u, ProgramDependenceGraph pdg) {
        Set<Unit> internalBackwardSlicing = new HashSet<>(128);

        PDGNode srcNode = findNodeOf(u, pdg);
        List<PDGNode> nodes = srcNode.getBackDependets();

        for (PDGNode n : nodes) {
            Iterator<Unit> iter = unitIteratorOfPDGNode(n);
            while (iter.hasNext()) {
                internalBackwardSlicing.add(iter.next());
            }
        }

        return internalBackwardSlicing;
    }

    /**
     * findDominators find all dominators of u in icfg
     *
     * @param u    the unit who wants to find its dominators
     * @param icfg the icfg where the unit lives at
     * @return     the set of domonators in the icfg of the unit
     */
    public static Set<Unit> findDominators(Unit u, IInfoflowCFG icfg) {
        try {
            SootMethod method = icfg.getMethodOf(u);
            DirectedGraph<Unit> graph = icfg.getOrCreateUnitGraph(method);
            MHGDominatorsFinder<Unit> mhgDominatorsFinder = new MHGDominatorsFinder<>(graph);
            return new HashSet<>(mhgDominatorsFinder.getDominators(u));
        } catch (Exception e) {
            return new HashSet<>();
        }
    }

    /**
     * findImmediateDominator finds the immediate dominator of u in icfg
     *
     * @param u    the unit who wants to find its immediate dominator
     * @param icfg the icfg where the unit lives at
     * @return     the immediate domonator in the icfg of the unit
     */
    public static Unit findImmediateDominator(Unit u, IInfoflowCFG icfg) {
        return new MHGDominatorsFinder<>(icfg.getOrCreateUnitGraph(icfg.getMethodOf(u)))
                .getImmediateDominator(u);
    }

    /**
     * findPostDominators find all post dominators of u in icfg
     *
     * @param u    the unit who wants to find its post dominators
     * @param icfg the icfg where the unit lives at
     * @return     the set of post domonators in the icfg of the unit
     */
    public static Set<Unit> findPostDominators(Unit u, IInfoflowCFG icfg) {
        SootMethod method = icfg.getMethodOf(u);
        DirectedGraph<Unit> graph = icfg.getOrCreateUnitGraph(method);
        MHGPostDominatorsFinder<Unit> mhgPostDominatorsFinder = new MHGPostDominatorsFinder<>(graph);
        return new HashSet<>(mhgPostDominatorsFinder.getDominators(u));
    }

    /**
     * findImmediatePostDominator finds the immediate post dominator of u in icfg
     *
     * @param u    the unit who wants to find its immediate post dominator
     * @param icfg the icfg where the unit lives at
     * @return     the immediate post domonator in the icfg of the unit
     */
    public static Unit findImmediatePostDominator(Unit u, IInfoflowCFG icfg) {
        return new MHGPostDominatorsFinder<>(icfg.getOrCreateUnitGraph(icfg.getMethodOf(u)))
                .getImmediateDominator(u);
    }

    /**
     * find backward slicing finds all the backward slicing of a unit
     *
     * @param u    the unit who wants to find its backward slicing
     * @param cg   the call graph where the unit lives at
     * @param icfg the icfg where the unit lives at
     * @return     the backward slicing of the unit in cg and icfg
     */
    public static Set<Unit> findBackwardSlcing(Unit u, CallGraph cg, IInfoflowCFG icfg) {
        Set<Unit> backwardSlicing = new HashSet<>();

        // a slicing includes the data-flow dependencies and control-flow dependencies
        // firstly we compute the data-flow dependencies using the call graph
        // secondly we compute the control-flow dependencies using the inter-procedural control flow graph

        // 1. data-flow dependencies
        backwardSlicing.addAll(findBackwardDataDependencies(u, icfg.getMethodOf(u), cg));

        // 2. control-flow dependencies
        backwardSlicing.addAll(findDominators(u, icfg));

        // 3. we use the built-in backward slicing to get the intra-procedural backward slicing
        try {
            backwardSlicing.addAll(
                    findInternalBackwardSlicing(
                            u,
                            new HashMutablePDG(new BriefUnitGraph(icfg.getMethodOf(u).getActiveBody()))));
        } catch (Exception e) {
            // do nothing here
        }

        return backwardSlicing;
    }

    /**
     * findCallSites gets the relatively complete set of call sites of a callee, given that the call graph
     * built in soot is incomplete, but firstly, we caches the invoking statements, and then we invoke
     * doFindCallSites to do actual finding.
     *
     * @param callee  the callee who wants to find its call sites
     * @param cg      the call graph needed traversing
     * @param classes the classes needed traversing
     * @param pkgs    the pkgs needed traversing
     * @return        the call sites of callee
     */
    public static Map<SootMethod, CallSites> findCallSites(
            SootMethod callee,
            CallGraph cg,
            Chain<SootClass> classes,
            List<String> pkgs) {
        // firstly, we traverse each soot method's body, caches the invoking statements
        if (invokingStmtsCache.isEmpty()) {
            for (SootClass c : classes) {
                // TODO a tool to filter 3rd-party libraries
                // These codes does not work for some debug-version apk, and actually, it does not work for
                // the released ones. We need a 3rd-party library elimination tool in practice. But here, we
                // simplify them. But for future use, we remain them here.
                if (false) {
                    boolean doAnalysis = false;
                    for (String s : pkgs) { if (c.getJavaPackageName().startsWith(s)) { doAnalysis = true; break; } }
                    if (!doAnalysis) { continue; }
                } else {
                    // filter some frequently used libraries
                    if (isIn3rdPartyLibrary(c.getJavaPackageName())) { continue; }
                }

                for (SootMethod m : c.getMethods()) {
                    try {
                        Body body = m.getActiveBody();
                        Chain<Unit> units = body.getUnits();
                        for (Unit u : units) {
                            if (!(u instanceof Stmt) || !((Stmt) u).containsInvokeExpr()) {
                                continue;
                            } else if (invokingStmtsCache.containsKey(m)) {
                                invokingStmtsCache.get(m).add(u);
                            } else {
                                invokingStmtsCache.put(m, new HashSet<>());
                            }
                        }
                    } catch (Exception e) {
                        // do nothing, some method may have no body, and a RuntimeException will be thrown
                    }
                }
            }
        }

        // then we get real
        return doFindCallSites(callee, cg);
    }

    /**
     * In javaDoc of Soot, the following information are mentioned:
     *
     *   This class(PDGNode) defines a Node in the Program Dependence
     *   Graph. There might be a need to store additional information
     *   in the PDG nodes. In essence, the PDG nodes represent (within
     *   them) either CFG nodes or Region nodes.
     *
     * So we simply considered that as only CFGNODE and REGION are allowed
     *
     * @param n the PDGNode to find
     * @return  iterator of n's units
     */
    public static Iterator<Unit> unitIteratorOfPDGNode(PDGNode n) {
        Iterator<Unit> iterator = null;
        PDGNode.Type type = n.getType();

        // get iterator
        if (type.equals(PDGNode.Type.CFGNODE)) {
            iterator = ((Block) n.getNode()).iterator();
        } else if (type.equals(PDGNode.Type.REGION)) {
            iterator = ((IRegion) n.getNode()).getUnitGraph().iterator();
        } else {
            logger.w("Only REGION and CFGNODE are allowed");
        }

        return iterator;
    }

    // doFindCallSites finds the relatively complete set of call sites of a callee, by:
    // 1. the built-in call graph
    // 2. the classes cached in invokingStmtsCache
    private static Map<SootMethod, CallSites> doFindCallSites(SootMethod callee, CallGraph cg) {
        Map<SootMethod, CallSites> callers = new HashMap<>(1);

        // firstly we get all callers from the incomplete call graph built in soot
        Iterator<Edge> edgeIterator = cg.edgesInto(callee);
        while (edgeIterator.hasNext()) {
            Edge       edge     = edgeIterator.next();
            SootMethod caller   = edge.src();
            Unit       callSite = edge.srcUnit();

            // TODO a tool to filter 3rd-party libraries
            // These codes does not work for some debug-version apk, and actually, it does not work for
            // the released ones. We need a 3rd-party library elimination tool in practice. But here, we
            // simplify them. But for future use, we remain them here.
            if (null == caller || null == caller.getDeclaringClass() ||
                    isIn3rdPartyLibrary(caller.getDeclaringClass().getJavaPackageName())) {
                continue ;
            } else if (callers.containsKey(caller)) {
                callers.get(caller).addCallSite(callSite);
            } else {
                callers.put(caller, new CallSites(callee, caller, callSite));
            }
        }

        // then we traverse all invoking statements, and check
        for (Map.Entry<SootMethod, Set<Unit>> entry : invokingStmtsCache.entrySet()) {
            for (Unit u : entry.getValue()) {
                InvokeExpr invokeExpr = ((Stmt) u).getInvokeExpr();
                if (callee.equals(invokeExpr.getMethod())) {
                    if (callers.containsKey(u)) {
                        callers.get(entry.getKey()).addCallSite(u);
                    } else {
                        callers.put(entry.getKey(), new CallSites(callee, entry.getKey(), u));
                    }
                }
            }
        }

        return callers;
    }

    // findBackwardDataDependencies finds the data-flow dependencies of u located at m in the call graph
    private static Set<Unit> findBackwardDataDependencies(Unit u, SootMethod m, CallGraph cg) {
        return findBackwardDataDependenciesExcept(u, m, cg, new HashSet<>());
    }

    // findBackwardDataDependenciesExcept finds the data-flow dependencies of u located at m in the call graph,
    // but exclude the exValues, i.e. do not trace them recursively
    private static Set<Unit>  findBackwardDataDependenciesExcept(
            Unit u,
            SootMethod m,
            CallGraph cg,
            Set<Value> exValues) {
        Set<Unit> ret = new HashSet<>();
        SootClass c = null == m ? null : m.getDeclaringClass();

        // TODO a tool to filter 3rd-party libraries
        // skip 3rd-party
        if (null == m || null == c || isIn3rdPartyLibrary(c.getJavaPackageName())) {
            return new HashSet<>();
        }

        // trick, we add all units in <clinit>(the static code block) and <init> into slicing
        // because these two blocks are sometimes the dependencies of other units
        try {
            for (SootMethod mm : c.getMethods()) {
                if (CLASS_STATIC_CODE_BLOCK_METHOD_NAME.equals(mm.getName()) ||
                        CLASS_CODE_BLOCK_METHOD_NAME.equals(mm.getName())) {
                    ret.addAll(mm.getActiveBody().getUnits());
                }
            }
        } catch (Exception e) { }

        for (ValueBox vb : u.getUseBoxes()) {
            Value v = vb.getValue();

            // ignore non-local values and excepted values
            if (!(v instanceof Local) || exValues.contains(v)) { continue; }

            // find those redefined statements
            Set<Unit> redefeindStmts = findPreviousDefinitions(v, u, m);
            // add theses redefined statements
            ret.addAll(redefeindStmts);
            // track these redefined statements
            for (Unit uu : redefeindStmts) {
                // when the redefined statement is redefined by a method arg, we continually track the caller
                if (uu instanceof IdentityStmt && ((IdentityStmt) uu).getRightOp() instanceof ParameterRef) {
                    int argIdx = ((ParameterRef) ((IdentityStmt) uu).getRightOp()).getIndex();
                    Map<SootMethod, CallSites> callSites = doFindCallSites(m, cg);

                    for (Map.Entry<SootMethod, CallSites> entry : callSites.entrySet()) {
                        // skip recursions
                        SootMethod caller = entry.getKey();
                        if (null != caller && caller.equals(m)) { continue; }
                        // not recursions
                        for (Unit callSite : entry.getValue().getCallSites()) {
                            assert callSite instanceof Stmt && ((Stmt) callSite).containsInvokeExpr();
                            Value      trackedArg    = ((Stmt) callSite).getInvokeExpr().getArg(argIdx);
                            Set<Value> untrackedArgs = new HashSet<>(((Stmt) callSite).getInvokeExpr().getArgs());
                            untrackedArgs.remove(trackedArg);
                            ret.addAll(findBackwardDataDependenciesExcept(callSite, entry.getKey(), cg, untrackedArgs));
                        }
                    }
                } else {
                    ret.addAll(findBackwardDataDependenciesExcept(uu, m, cg, new HashSet<>()));
                }
            }
        }

        return ret;
    }

    // TODO a 3rd-party libraries elimination tool, like LibScout
    // isIn3rdPartyLibrary checks if the signature represented api is a 3rd-party one,
    // we implements them here with a black list
    private static boolean isIn3rdPartyLibrary(String signature) {
        List<String> blackList = Arrays.asList(
            "android",          // android official
            "java",             // java official
            "com.jakewharton",  // butterknife
            "com.github",       // github related, glide, etc.
            "com.squareup",     // okhttp, retrofit, etc.
            "com.google",       // google related, gson, guava, etc.
            "com.crashlytcics", // firebase
            "org.omg",          // extends to java
            "org.w3c",          // extends to java
            "org.xml",          // extends to java
            "org.apache",       // apache organization
            "com.sun",          // sun
            "org.eclipse",      // eclipse
            "rx"                // reactivex java
        );

        for (String s : blackList) {
            if (signature.startsWith(s)) { return true; }
        }

        return false;
    }

}
