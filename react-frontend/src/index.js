import React from "react";
import ReactDOM from "react-dom/client";
import "./index.css";
import App from "./App";
import { initializeKeycloak } from "./auth/keycloak";

const root = ReactDOM.createRoot(document.getElementById("root"));

async function bootstrap() {
    // Xóa dữ liệu đăng nhập của cơ chế cũ.
    localStorage.removeItem("libraryAuth");

    try {
        await initializeKeycloak();

        root.render(
            <React.StrictMode>
                <App />
            </React.StrictMode>,
        );
    } catch {
        root.render(
            <main>
                <h1>Không kết nối được hệ thống đăng nhập</h1>
                <p>Kiểm tra Keycloak rồi tải lại trang.</p>
                <button onClick={() => window.location.reload()}>
                    Thử lại
                </button>
            </main>,
        );
    }
}

bootstrap();
