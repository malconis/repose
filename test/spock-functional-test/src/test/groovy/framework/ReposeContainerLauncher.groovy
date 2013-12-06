package framework

import org.linkedin.util.clock.SystemClock

import java.nio.charset.Charset

import static org.linkedin.groovy.util.concurrent.GroovyConcurrentUtils.waitForCondition

class ReposeContainerLauncher extends AbstractReposeLauncher {

    int shutdownPort
    int reposePort

    String clusterId
    String nodeId

    String containerJar
    String rootWarLocation
    String[] appWars

    def clock = new SystemClock()
    def Process process

    ReposeContainerLauncher(ReposeConfigurationProvider configurationProvider, String containerJar,
                            String clusterId = "cluster1", String nodeId = "node1",
                            String rootWarLocation, int reposePort, int stopPort, String... appWars) {
        this.configurationProvider = configurationProvider
        this.containerJar = containerJar
        this.clusterId = clusterId
        this.nodeId = nodeId
        this.reposePort = reposePort
        this.rootWarLocation = rootWarLocation
        this.shutdownPort = stopPort
        this.appWars = appWars
    }

    @Override
    void start() {
        String configDirectory = configurationProvider.getReposeConfigDir()

        String webXmlOverrides = "-Dpowerapi-config-directory=${configDirectory} -Drepose-cluster-id=${clusterId} -Drepose-node-id=${nodeId}"

        def cmd = "java ${webXmlOverrides} -jar ${containerJar} -p ${reposePort} -w ${rootWarLocation} -s ${shutdownPort}"

        if (appWars != null || appWars.length != 0) {
            for (String path : appWars) {
                cmd += " -war ${path}"
            }
        }
        println("Starting repose: ${cmd}")

        def th = new Thread({ this.process = cmd.execute() });

        th.run()
        th.join()
    }

    @Override
    void stop() {
        try {
            final Socket s = new Socket(InetAddress.getByName("127.0.0.1"), shutdownPort);
            final OutputStream out = s.getOutputStream();

            println("Sending Repose stop request to port $shutdownPort");

            out.write(("\r\n").getBytes(Charset.forName("UTF-8")));
            out.flush();
            s.close();

            print("Waiting for Repose Container to shutdown")
            waitForCondition(clock, "4000", '1s', {
                print(".")
                !isUp()
            })
            println()

        } catch (IOException ioex) {

            this.process.waitForOrKill(5000)
            killIfUp()
            println("An error occurred while attempting to stop Repose Controller. Reason: " + ioex.getMessage());
        }
    }

    private void killIfUp() {
        String processes = TestUtils.getJvmProcesses()
        def regex = /(\d*) ROOT.war -s ${shutdownPort} .*/
        def matcher = (processes =~ regex)
        if (matcher.size() > 0) {

            for (int i=1;i<=matcher.size();i++){
                String pid = matcher[0][i]

                if (pid!=null && !pid.isEmpty()) {
                    println("Killing running repose-valve process: " + pid)
                    Runtime rt = Runtime.getRuntime();
                    if (System.getProperty("os.name").toLowerCase().indexOf("windows") > -1)
                        rt.exec("taskkill " + pid.toInteger());
                    else
                        rt.exec("kill -9 " + pid.toInteger());
                }
            }
        }
    }


    @Override
    boolean isUp() {
        return TestUtils.getJvmProcesses().contains("ROOT.war")
    }

}
