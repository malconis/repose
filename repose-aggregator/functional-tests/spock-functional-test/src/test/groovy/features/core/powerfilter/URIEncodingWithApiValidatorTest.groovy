package features.core.powerfilter

import framework.ReposeValveTest
import org.rackspace.deproxy.Deproxy
import spock.lang.Unroll

class URIEncodingWithApiValidatorTest extends ReposeValveTest {

    def setupSpec() {
        deproxy = new Deproxy()
        deproxy.addEndpoint(properties.targetPort)

        def params = properties.getDefaultTemplateParams()
        repose.configurationProvider.applyConfigs("common", params)
        repose.configurationProvider.applyConfigs("features/core/powerfilter/URIEncode/withAPIValidator", params)
        repose.start(killOthersBeforeStarting: false, waitOnJmxAfterStarting: false)
        repose.waitForNon500FromUrl(reposeEndpoint, 120)
    }

    @Unroll("URI's with special character through API Validator filter sent = #URISent")
    def "API Validator filter handles '+' in URI path"() {

        when: "User sends a request through repose"
        def messageChain = deproxy.makeRequest(url: reposeEndpoint, path: URISent, method: "GET", headers: ["X-Roles": "role-1"])

        then: "Repose send the URI parameters without manipulation"
        messageChain.receivedResponse.code.equals("200")
        messageChain.handlings.size() == 1
        messageChain.handlings.get(0).request.path == URItoriginService

        where:
        URISent                    | URItoriginService
        "/+messages?ids=locations" | "/+messages?ids=locations"
        "/messages/+add-nodes"     | "/messages/+add-nodes"
    }

    def cleanupSpec() {
        if (repose) {
            repose.stop()
        }

        if (deproxy) {
            deproxy.shutdown()
        }
    }
}
