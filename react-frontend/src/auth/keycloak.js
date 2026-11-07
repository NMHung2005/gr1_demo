import Keycloak from "keycloak-js";

const keycloak = new Keycloak({
    url: "http://localhost:8081",
    realm: "library",
    clientId: "library-frontend",
});

let initialization;

export function initializeKeycloak() {
    if (!initialization) {
        initialization = keycloak.init({
            onLoad: "check-sso",
            pkceMethod: "S256",
            checkLoginIframe: false,
            redirectUri: `${window.location.origin}${window.location.pathname}${window.location.search}`,
        });
    }

    return initialization;
}

export default keycloak;
