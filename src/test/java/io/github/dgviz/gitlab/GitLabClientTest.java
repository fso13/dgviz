package io.github.dgviz.gitlab;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GitLabClientTest {

    private MockWebServer server;
    private GitLabClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new GitLabClient(server.url("/").toString().replaceAll("/$", ""), "test-token");
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void shouldListProjectsWithPagination() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                [{"id":1,"name":"a","path_with_namespace":"g/a","web_url":"http://x/a"}]
                """).addHeader("Content-Type", "application/json"));

        List<GitLabClient.GitLabProject> projects = client.listProjects();

        assertThat(projects).hasSize(1);
        assertThat(projects.get(0).name()).isEqualTo("a");
        assertThat(server.takeRequest().getHeader("PRIVATE-TOKEN")).isEqualTo("test-token");
    }

    @Test
    void shouldFetchFileContent() throws Exception {
        server.enqueue(new MockResponse().setBody("<project></project>"));

        String content = client.getFileContent("group/proj", "pom.xml", "main");

        assertThat(content).contains("<project>");
        assertThat(server.takeRequest().getPath()).contains("/repository/files/pom.xml/raw");
    }
}
