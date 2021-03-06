package com.messners.gitlab.api;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

/**
 * This class utilizes the Jersey client package to communicate with a GitLab API endpoint.
 * 
 * @author Greg Messner <greg@messners.com>
 */
public class GitLabApiClient {
	
	protected static final String PRIVATE_TOKEN_HEADER = "PRIVATE-TOKEN";
	protected static final String API_NAMESPACE = "/api/v3";
	
	private ClientConfig clientConfig;
	private Client apiClient;
	private String hostUrl;
	private String privateToken;
	private static boolean ignoreCertificateErrors;
	private static SSLSocketFactory defaultSocketFactory;


	/**
	 * Construct an instance to communicate with a GitLab API server using the specified
	 * server URL and private token.
	 * 
	 * @param hostUrl the URL to the GitLab API server
	 * @param privateToken the private token to authenticate with
	 */
	public GitLabApiClient (String hostUrl, String privateToken) {	
		
		// Remove the trailing "/" from the hostUrl if present
		this.hostUrl = (hostUrl.endsWith("/") ? hostUrl.replaceAll("/$", "") : hostUrl) + API_NAMESPACE;
		this.privateToken = privateToken;

		clientConfig = new ClientConfig();
		clientConfig.register(JacksonJson.class);
	}
	
	
	/**
	 * Returns true if the API is setup to ignore SSL certificate errors, otherwise returns false.
	 * 
	 * @return true if the API is setup to ignore SSL certificate errors, otherwise returns false
	 */
	public boolean getIgnoreCertificateErrors () {
		return (GitLabApiClient.ignoreCertificateErrors);
	}
	
	
	/**
	 * Sets up the Jersey system ignore SSL certificate errors or not.
	 * 
	 * <p><strong>WARNING: Setting this to true will affect ALL uses of HttpsURLConnection and Jersey.<strong><p>
	 * 
	 * @param ignoreCertificateErrors
	 */
	public void setIgnoreCerificateErrors (boolean ignoreCertificateErrors) {
		
		if (GitLabApiClient.ignoreCertificateErrors == ignoreCertificateErrors) {
			return;
		}
		
		if (ignoreCertificateErrors == false) {	
			GitLabApiClient.ignoreCertificateErrors = false;
			HttpsURLConnection.setDefaultSSLSocketFactory(GitLabApiClient.defaultSocketFactory);
			return;
		}
		
		SSLSocketFactory defaultSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory();

		if (ignoreCertificateErrors() == true) {
			GitLabApiClient.ignoreCertificateErrors = true;
			GitLabApiClient.defaultSocketFactory = defaultSocketFactory;
		} else {
			throw new RuntimeException("Unable to ignore certificate errors.");
		}
	}
	
	
	/**
	 * Sets up Jersey client to ignore certificate errors.  
	 *
	 * @return true if successful at setting up to ignore certificate errors, otherwise returns false.
	 */
	private boolean ignoreCertificateErrors () {

		// Create a TrustManager that trusts all certificates
		TrustManager[ ] certs = new TrustManager[ ] {
            new X509TrustManager() {
				@Override
				public X509Certificate[] getAcceptedIssuers() {
					return null;
				}
				@Override
				public void checkServerTrusted(X509Certificate[] chain, String authType)
						throws CertificateException {
				}
				@Override
				public void checkClientTrusted(X509Certificate[] chain, String authType)
						throws CertificateException {
				}
			}
		};
    
		// Now set the default SSLSocketFactory to use the just created TrustManager
		SSLSocketFactory defaultSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
		SSLContext sslContext = null;
		try {
			sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, certs, new SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
			HttpsURLConnection.getDefaultSSLSocketFactory();
		} catch (GeneralSecurityException ex) {
			HttpsURLConnection.setDefaultSSLSocketFactory(defaultSocketFactory);
			return (false);
		}
    
		return (true);
	}
    
	
	/**
	 * Construct a REST URL with the specified path arguments.
	 * 
	 * @param pathArgs
	 * @return a REST URL with the specified path arguments
	 * @throws IOException
	 */
	protected URL getApiUrl (Object ... pathArgs) throws IOException {
		
		StringBuilder url = new StringBuilder();
		url.append(hostUrl);
		for (Object pathArg : pathArgs) {
			url.append("/");
			url.append(pathArg.toString());
		}
		
		return (new URL(url.toString()));
    }

	
	/**
	 * Perform an HTTP GET call with the specified query parameters and path objects, returning 
	 * a ClientResponse instance with the data returned from the endpoint.
	 * 
	 * @param queryParams
	 * @param pathArgs
	 * @return a ClientResponse instance with the data returned from the endpoint
	 * @throws IOException
	 */
	protected Response get (MultivaluedMap<String, String> queryParams, Object ... pathArgs)
			throws IOException {
		URL url = getApiUrl(pathArgs);
		return (get(queryParams, url));	
	}	
	
	
	/**
	 * Perform an HTTP GET call with the specified query parameters and URL, returning 
	 * a ClientResponse instance with the data returned from the endpoint.
	 * 
	 * @param queryParams
	 * @param url
	 * @return a ClientResponse instance with the data returned from the endpoint
	 */
	protected Response get (MultivaluedMap<String, String> queryParams, URL url) {
		return invocation(url, queryParams).get();
	}
	
	
	/**
	 * Perform an HTTP POST call with the specified form data and path objects, returning 
	 * a ClientResponse instance with the data returned from the endpoint.
	 * 
	 * @param formData
	 * @param pathArgs
	 * @return a ClientResponse instance with the data returned from the endpoint
	 * @throws IOException
	 */
	protected Response post (Form formData, Object ... pathArgs)
			throws IOException {
		URL url = getApiUrl(pathArgs);
		return post(formData, url);
	}
	

	/**
	 * Perform an HTTP POST call with the specified form data and URL, returning 
	 * a ClientResponse instance with the data returned from the endpoint.
	 * 
	 * @param formData
	 * @param url
	 * @return a ClientResponse instance with the data returned from the endpoint
	 */
	protected Response post (Form formData, URL url) {
		return invocation(url, null).post(Entity.entity(formData, MediaType.APPLICATION_FORM_URLENCODED_TYPE));
	}
	
	
	/**
	 * Perform an HTTP PUT call with the specified form data and path objects, returning 
	 * a ClientResponse instance with the data returned from the endpoint.
	 * 
	 * @param queryParams
	 * @param pathArgs
	 * @return a ClientResponse instance with the data returned from the endpoint
	 * @throws IOException
	 */
	protected  Response put (MultivaluedMap<String, String> queryParams, Object ... pathArgs)
			throws IOException {
		URL url = getApiUrl(pathArgs);
		return (put(queryParams, url));	
	}	
	
	
	/**
	 * Perform an HTTP PUT call with the specified form data and URL, returning 
	 * a ClientResponse instance with the data returned from the endpoint.
	 *  
	 * @param queryParams
	 * @param url
	 * @return a ClientResponse instance with the data returned from the endpoint
	 */
	protected Response put (MultivaluedMap<String, String> queryParams, URL url) {
	    return invocation(url, null).put(Entity.entity(queryParams, MediaType.APPLICATION_FORM_URLENCODED_TYPE));
	}
	
	
	/**
	 * Perform an HTTP DELETE call with the specified form data and path objects, returning 
	 * a Response instance with the data returned from the endpoint.
	 * 
	 * @param queryParams
	 * @param pathArgs
	 * @return a Response instance with the data returned from the endpoint
	 * @throws IOException
	 */
	protected Response delete (MultivaluedMap<String, String> queryParams, Object ... pathArgs) throws IOException {
		return delete(queryParams, getApiUrl(pathArgs));
	}	
	
	
	/**
	 * Perform an HTTP DELETE call with the specified form data and URL, returning 
	 * a Response instance with the data returned from the endpoint.
	 *  
	 * @param queryParams
	 * @param url
	 * @return a Response instance with the data returned from the endpoint
	 */
	protected Response delete (MultivaluedMap<String, String> queryParams, URL url) {
		return invocation(url, queryParams).delete();
	}

	protected class AcceptAllHostnameVerifier implements HostnameVerifier {
		@Override
		public boolean verify(String s, SSLSession sslSession) {
			return true;
		}
	}

	protected Invocation.Builder invocation(URL url, MultivaluedMap<String, String> queryParams) {
		if (apiClient == null) {
			apiClient = ClientBuilder.newBuilder()
					.withConfig(clientConfig)
					.sslContext(getSslContext())
					.hostnameVerifier(new AcceptAllHostnameVerifier())
					.build();
		}

		WebTarget target = apiClient.target(url.toExternalForm()).property(ClientProperties.FOLLOW_REDIRECTS, true);
		if (queryParams != null) {
			for (Map.Entry<String, List<String>> param : queryParams.entrySet()) {
				target = target.queryParam(param.getKey(), param.getValue().toArray());
			}
		}
		return target.request().header(PRIVATE_TOKEN_HEADER, privateToken).accept(MediaType.APPLICATION_JSON);
	}

	private SSLContext getSslContext() {
		try {
			return SSLContext.getDefault();
		} catch (NoSuchAlgorithmException e) {
			throw new UnsupportedOperationException(e);
		}
	}


}
